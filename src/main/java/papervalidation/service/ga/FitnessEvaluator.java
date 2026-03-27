package papervalidation.service.ga;

import papervalidation.domain.ga.Chromosome;
import papervalidation.domain.ga.SruGene;
import papervalidation.domain.raster.PocRaster;
import papervalidation.domain.searchplan.SearchArea;
import papervalidation.domain.sru.Sru;
import papervalidation.service.searchplan.PosCalculator;
import papervalidation.util.CoordinateConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Fitness function: totalPOS - penalties.
 */
public class FitnessEvaluator {

    private final PocRaster raster;
    private final List<Sru> srus;
    private final PosCalculator posCalculator;

    public FitnessEvaluator(PocRaster raster, List<Sru> srus, PosCalculator posCalculator) {
        this.raster = raster;
        this.srus = srus;
        this.posCalculator = posCalculator;
    }

    public double evaluate(Chromosome chromosome) {
        List<SearchArea> areas = toSearchAreas(chromosome);
        double totalPOS = posCalculator.calculateTotalPos(raster, areas, srus);

        double penaltyOverlap = calculateOverlapPenalty(chromosome);
        double penaltySoft = calculateSoftPenalty(chromosome);

        double fitness = totalPOS - penaltyOverlap - penaltySoft;
        chromosome.setFitness(fitness);
        return fitness;
    }

    private List<SearchArea> toSearchAreas(Chromosome chromosome) {
        List<SearchArea> areas = new ArrayList<>();
        for (SruGene gene : chromosome.getGenes()) {
            areas.add(new SearchArea(
                    gene.getSruId(),
                    gene.getCenterX(), gene.getCenterY(),
                    gene.getWidth(), gene.getHeight(),
                    gene.getRotation()
            ));
        }
        return areas;
    }

    private double calculateOverlapPenalty(Chromosome chromosome) {
        SruGene[] genes = chromosome.getGenes();
        double penalty = 0;

        for (int i = 0; i < genes.length; i++) {
            for (int j = i + 1; j < genes.length; j++) {
                // Only penalize same-altitude overlap
                if (genes[i].getAltitudeIndex() != genes[j].getAltitudeIndex()) continue;

                double overlapRatio = estimateOverlap(genes[i], genes[j]);
                if (overlapRatio > 0) {
                    penalty += 1e6 * (1 + overlapRatio);
                }
            }
        }
        return penalty;
    }

    private double estimateOverlap(SruGene g1, SruGene g2) {
        // Simplified AABB overlap check (ignoring rotation for speed)
        double buffer = 200; // 200m safety buffer
        double hw1 = g1.getWidth() / 2 + buffer;
        double hh1 = g1.getHeight() / 2 + buffer;
        double hw2 = g2.getWidth() / 2 + buffer;
        double hh2 = g2.getHeight() / 2 + buffer;

        double overlapX = Math.max(0, Math.min(g1.getCenterX() + hw1, g2.getCenterX() + hw2)
                - Math.max(g1.getCenterX() - hw1, g2.getCenterX() - hw2));
        double overlapY = Math.max(0, Math.min(g1.getCenterY() + hh1, g2.getCenterY() + hh2)
                - Math.max(g1.getCenterY() - hh1, g2.getCenterY() - hh2));

        if (overlapX <= 0 || overlapY <= 0) return 0;

        double overlapArea = overlapX * overlapY;
        double totalArea = (2 * hw1) * (2 * hh1) + (2 * hw2) * (2 * hh2);
        return overlapArea / totalArea;
    }

    private double calculateSoftPenalty(Chromosome chromosome) {
        double penalty = 0;
        for (int i = 0; i < chromosome.getGenes().length; i++) {
            SruGene gene = chromosome.getGenes()[i];
            Sru sru = findSru(gene.getSruId());
            if (sru == null) continue;

            // Coverage factor penalty
            double lat = CoordinateConverter.yToLat(gene.getCenterY());
            SearchArea sa = new SearchArea(gene.getSruId(), gene.getCenterX(), gene.getCenterY(),
                    gene.getWidth(), gene.getHeight(), gene.getRotation());
            double areaNm2 = posCalculator.calculateAreaNm2(sa, lat);
            double C = posCalculator.calculateCoverageFactor(sru, areaNm2);
            double cClamped = Math.max(0.5, Math.min(3.0, C));
            penalty += 10.0 * Math.abs(C - cClamped);

            // Aspect ratio penalty
            double aspectRatio = (gene.getHeight() > 0) ? gene.getWidth() / gene.getHeight() : 1;
            double rClamped = Math.max(0.3, Math.min(3.0, aspectRatio));
            penalty += 10.0 * Math.abs(aspectRatio - rClamped);
        }
        return penalty;
    }

    private Sru findSru(String id) {
        return srus.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }
}
