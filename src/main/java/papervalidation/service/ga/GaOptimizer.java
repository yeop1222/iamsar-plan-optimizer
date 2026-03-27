package papervalidation.service.ga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import papervalidation.domain.ga.Chromosome;
import papervalidation.domain.ga.GaConfig;
import papervalidation.domain.ga.SruGene;
import papervalidation.domain.raster.PocRaster;
import papervalidation.domain.searchplan.SearchArea;
import papervalidation.domain.searchplan.SearchPlanResult;
import papervalidation.domain.sru.Sru;
import papervalidation.domain.sru.SruType;
import papervalidation.service.raster.RasterService;
import papervalidation.service.searchplan.PosCalculator;
import papervalidation.service.sru.SruService;
import papervalidation.service.sru.SweepWidthTable;
import papervalidation.util.CoordinateConverter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@Service
public class GaOptimizer {

    private static final Logger log = LoggerFactory.getLogger(GaOptimizer.class);

    private final RasterService rasterService;
    private final SruService sruService;
    private final PosCalculator posCalculator;
    private final AtomicInteger optimizationCounter = new AtomicInteger(0);
    private final Map<String, Object[]> progressMap = new ConcurrentHashMap<>();

    public GaOptimizer(RasterService rasterService, SruService sruService, PosCalculator posCalculator) {
        this.rasterService = rasterService;
        this.sruService = sruService;
        this.posCalculator = posCalculator;
    }

    /**
     * Run GA optimization. Calls progressCallback(generation, data) for each generation.
     */
    public SearchPlanResult optimize(GaConfig config, BiConsumer<Integer, Map<String, Object>> progressCallback) {
        PocRaster raster = rasterService.getCurrentRaster();
        if (raster == null) throw new IllegalStateException("No POC raster");

        List<Sru> srus = sruService.getAll();
        if (srus.isEmpty()) throw new IllegalStateException("No SRUs registered");

        Random random = new Random();
        int nSru = srus.size();

        FitnessEvaluator fitnessEval = new FitnessEvaluator(raster, srus, posCalculator);
        SelectionOperator selection = new SelectionOperator(config.getTournamentSize(), random);
        CrossoverOperator crossover = new CrossoverOperator(config.getCrossoverRate(), random);
        MutationOperator mutation = new MutationOperator(config.getMutationRate(), random);
        OverlapRepairer repairer = new OverlapRepairer();

        // 1. Initialize population
        List<Chromosome> population = new ArrayList<>();
        int hotspotCount = (int) (config.getPopulationSize() * 0.2);
        for (int i = 0; i < config.getPopulationSize(); i++) {
            Chromosome c = (i < hotspotCount)
                    ? createHotspotSeeded(raster, srus, random)
                    : createRandom(raster, srus, random);
            fitnessEval.evaluate(c);
            population.add(c);
        }

        double bestFitness = Double.NEGATIVE_INFINITY;
        int stagnation = 0;
        Chromosome bestEver = null;

        // 2. Evolution loop
        for (int gen = 0; gen < config.getMaxGenerations(); gen++) {
            population.sort(null); // descending fitness

            // Track best
            if (population.get(0).getFitness() > bestFitness) {
                bestFitness = population.get(0).getFitness();
                bestEver = population.get(0).copy();
                stagnation = 0;
            } else {
                stagnation++;
            }

            // Progress callback
            if (progressCallback != null) {
                double avgFitness = population.stream().mapToDouble(Chromosome::getFitness).average().orElse(0);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("generation", gen);
                data.put("bestFitness", bestFitness);
                data.put("avgFitness", avgFitness);
                progressCallback.accept(gen, data);
            }

            // Convergence check
            if (stagnation >= config.getConvergenceGenerations()) {
                log.info("GA converged at generation {} with fitness {}", gen, bestFitness);
                break;
            }

            // 2a. Elitism
            List<Chromosome> nextGen = new ArrayList<>();
            for (int e = 0; e < config.getElitismCount() && e < population.size(); e++) {
                nextGen.add(population.get(e).copy());
            }

            // 2b. Selection + Crossover + Mutation
            while (nextGen.size() < config.getPopulationSize()) {
                Chromosome p1 = selection.select(population);
                Chromosome p2 = selection.select(population);
                Chromosome[] children = crossover.crossover(p1, p2);

                for (Chromosome child : children) {
                    if (nextGen.size() >= config.getPopulationSize()) break;
                    mutation.mutate(child, raster, srus);
                    repairer.repair(child, raster);
                    fitnessEval.evaluate(child);
                    nextGen.add(child);
                }
            }

            population = nextGen;
        }

        // 3. Return best
        if (bestEver == null) {
            population.sort(null);
            bestEver = population.get(0);
        }

        return buildResult(bestEver, raster, srus);
    }

    private Chromosome createRandom(PocRaster raster, List<Sru> srus, Random random) {
        Chromosome c = new Chromosome(srus.size());
        for (int i = 0; i < srus.size(); i++) {
            c.getGenes()[i] = randomGene(srus.get(i), raster, random);
        }
        return c;
    }

    private Chromosome createHotspotSeeded(PocRaster raster, List<Sru> srus, Random random) {
        // Find POC-weighted centroid
        double sumX = 0, sumY = 0, sumP = 0;
        for (int r = 0; r < raster.getRows(); r++) {
            for (int c = 0; c < raster.getCols(); c++) {
                double p = raster.getData()[r][c];
                if (p > 0) {
                    sumX += raster.getCellCenterX(c) * p;
                    sumY += raster.getCellCenterY(r) * p;
                    sumP += p;
                }
            }
        }

        double cx = sumP > 0 ? sumX / sumP : (raster.getOriginX() + raster.getMaxX()) / 2;
        double cy = sumP > 0 ? sumY / sumP : (raster.getOriginY() + raster.getMaxY()) / 2;

        Chromosome c = new Chromosome(srus.size());
        double rangeX = raster.getMaxX() - raster.getOriginX();
        double rangeY = raster.getMaxY() - raster.getOriginY();

        for (int i = 0; i < srus.size(); i++) {
            SruGene gene = randomGene(srus.get(i), raster, random);
            // Bias toward centroid
            gene.setCenterX(cx + random.nextGaussian() * rangeX * 0.15);
            gene.setCenterY(cy + random.nextGaussian() * rangeY * 0.15);
            gene.setCenterX(clamp(gene.getCenterX(), raster.getOriginX(), raster.getMaxX()));
            gene.setCenterY(clamp(gene.getCenterY(), raster.getOriginY(), raster.getMaxY()));
            c.getGenes()[i] = gene;
        }
        return c;
    }

    private SruGene randomGene(Sru sru, PocRaster raster, Random random) {
        double cx = raster.getOriginX() + random.nextDouble() * (raster.getMaxX() - raster.getOriginX());
        double cy = raster.getOriginY() + random.nextDouble() * (raster.getMaxY() - raster.getOriginY());

        // Base area from Z (search effort)
        double Z = sru.getEffectiveSweepWidth() * sru.getSearchSpeed() * sru.getEndurance(); // NM²
        double baseAreaM = Z * 1852 * 1852; // convert to m²
        double baseSide = Math.sqrt(baseAreaM);
        double scaleFactor = 0.5 + random.nextDouble() * 2.5; // 0.5x ~ 3.0x
        double w = baseSide * scaleFactor * (0.5 + random.nextDouble());
        double h = baseSide * scaleFactor * (0.5 + random.nextDouble());

        double rotation = random.nextDouble() * 360;
        int altIdx = sru.getType().isSurface() ? 0 : random.nextInt(SweepWidthTable.ALTITUDE_LEVELS.length);

        return new SruGene(sru.getId(), cx, cy, Math.max(100, w), Math.max(100, h), rotation, altIdx);
    }

    private SearchPlanResult buildResult(Chromosome best, PocRaster raster, List<Sru> srus) {
        List<SearchArea> areas = new ArrayList<>();
        List<SearchPlanResult.SruResult> sruResults = new ArrayList<>();

        for (SruGene gene : best.getGenes()) {
            SearchArea sa = new SearchArea(gene.getSruId(), gene.getCenterX(), gene.getCenterY(),
                    gene.getWidth(), gene.getHeight(), gene.getRotation());
            areas.add(sa);

            Sru sru = srus.stream().filter(s -> s.getId().equals(gene.getSruId())).findFirst().orElse(null);
            if (sru != null) {
                double poc = posCalculator.sumPocInRect(raster, sa);
                double lat = CoordinateConverter.yToLat(sa.getCenterY());
                double areaNm2 = posCalculator.calculateAreaNm2(sa, lat);
                double C = posCalculator.calculateCoverageFactor(sru, areaNm2);
                double pod = posCalculator.calculatePod(C);
                double pos = poc * pod;
                sruResults.add(new SearchPlanResult.SruResult(sru.getId(), sru.getName(), areaNm2, poc, C, pod, pos));
            }
        }

        double totalPOS = posCalculator.calculateTotalPos(raster, areas, srus);

        SearchPlanResult result = new SearchPlanResult();
        result.setTotalPOS(totalPOS);
        result.setSruResults(sruResults);
        result.setAreas(areas);
        result.setMethod("GA");
        result.setDescription("GA Optimization");
        return result;
    }

    public String createOptimizationId() {
        return "opt-" + optimizationCounter.incrementAndGet();
    }

    public Map<String, Object[]> getProgressMap() {
        return progressMap;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
