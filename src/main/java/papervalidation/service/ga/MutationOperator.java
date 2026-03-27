package papervalidation.service.ga;

import papervalidation.domain.ga.Chromosome;
import papervalidation.domain.ga.SruGene;
import papervalidation.domain.raster.PocRaster;
import papervalidation.domain.sru.Sru;
import papervalidation.domain.sru.SruType;
import papervalidation.service.sru.SweepWidthTable;

import java.util.List;
import java.util.Random;

/**
 * Gaussian mutation for each variable with given mutation rate.
 */
public class MutationOperator {

    private final double mutationRate;
    private final Random random;

    public MutationOperator(double mutationRate, Random random) {
        this.mutationRate = mutationRate;
        this.random = random;
    }

    public void mutate(Chromosome chromosome, PocRaster raster, List<Sru> srus) {
        double rangeX = raster.getMaxX() - raster.getOriginX();
        double rangeY = raster.getMaxY() - raster.getOriginY();

        for (int i = 0; i < chromosome.getGenes().length; i++) {
            SruGene gene = chromosome.getGenes()[i];
            Sru sru = findSru(srus, gene.getSruId());

            // Center: ±10% of raster range
            if (random.nextDouble() < mutationRate) {
                gene.setCenterX(gene.getCenterX() + random.nextGaussian() * rangeX * 0.1);
            }
            if (random.nextDouble() < mutationRate) {
                gene.setCenterY(gene.getCenterY() + random.nextGaussian() * rangeY * 0.1);
            }

            // Size: multiply by (1 + gaussian * 0.2)
            if (random.nextDouble() < mutationRate) {
                gene.setWidth(Math.max(100, gene.getWidth() * (1 + random.nextGaussian() * 0.2)));
            }
            if (random.nextDouble() < mutationRate) {
                gene.setHeight(Math.max(100, gene.getHeight() * (1 + random.nextGaussian() * 0.2)));
            }

            // Rotation: ±15 degrees
            if (random.nextDouble() < mutationRate) {
                double newRot = gene.getRotation() + random.nextGaussian() * 15;
                gene.setRotation(((newRot % 360) + 360) % 360);
            }

            // Altitude: uniform random for aircraft
            if (sru != null && sru.getType().isAircraft()) {
                if (random.nextDouble() < mutationRate) {
                    gene.setAltitudeIndex(random.nextInt(SweepWidthTable.ALTITUDE_LEVELS.length));
                }
            }

            // Clamp center to raster bounds
            gene.setCenterX(clamp(gene.getCenterX(), raster.getOriginX(), raster.getMaxX()));
            gene.setCenterY(clamp(gene.getCenterY(), raster.getOriginY(), raster.getMaxY()));
        }
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private Sru findSru(List<Sru> srus, String id) {
        return srus.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }
}
