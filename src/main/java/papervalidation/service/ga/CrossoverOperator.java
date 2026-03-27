package papervalidation.service.ga;

import papervalidation.domain.ga.Chromosome;
import papervalidation.domain.ga.SruGene;

import java.util.Random;

/**
 * SBX (Simulated Binary Crossover) for continuous variables.
 * η_c = 20
 */
public class CrossoverOperator {

    private static final double ETA_C = 20.0;
    private final double crossoverRate;
    private final Random random;

    public CrossoverOperator(double crossoverRate, Random random) {
        this.crossoverRate = crossoverRate;
        this.random = random;
    }

    public Chromosome[] crossover(Chromosome p1, Chromosome p2) {
        Chromosome c1 = p1.copy();
        Chromosome c2 = p2.copy();

        if (random.nextDouble() > crossoverRate) {
            return new Chromosome[]{c1, c2};
        }

        for (int i = 0; i < c1.getGenes().length; i++) {
            SruGene g1 = c1.getGenes()[i];
            SruGene g2 = c2.getGenes()[i];

            double[] cxResult = sbx(p1.getGenes()[i].getCenterX(), p2.getGenes()[i].getCenterX());
            g1.setCenterX(cxResult[0]);
            g2.setCenterX(cxResult[1]);

            double[] cyResult = sbx(p1.getGenes()[i].getCenterY(), p2.getGenes()[i].getCenterY());
            g1.setCenterY(cyResult[0]);
            g2.setCenterY(cyResult[1]);

            double[] wResult = sbx(p1.getGenes()[i].getWidth(), p2.getGenes()[i].getWidth());
            g1.setWidth(Math.max(100, wResult[0]));
            g2.setWidth(Math.max(100, wResult[1]));

            double[] hResult = sbx(p1.getGenes()[i].getHeight(), p2.getGenes()[i].getHeight());
            g1.setHeight(Math.max(100, hResult[0]));
            g2.setHeight(Math.max(100, hResult[1]));

            double[] rResult = sbx(p1.getGenes()[i].getRotation(), p2.getGenes()[i].getRotation());
            g1.setRotation(((rResult[0] % 360) + 360) % 360);
            g2.setRotation(((rResult[1] % 360) + 360) % 360);
        }

        return new Chromosome[]{c1, c2};
    }

    private double[] sbx(double v1, double v2) {
        double u = random.nextDouble();
        double beta;
        if (u <= 0.5) {
            beta = Math.pow(2.0 * u, 1.0 / (ETA_C + 1.0));
        } else {
            beta = Math.pow(1.0 / (2.0 * (1.0 - u)), 1.0 / (ETA_C + 1.0));
        }
        double child1 = 0.5 * ((1 + beta) * v1 + (1 - beta) * v2);
        double child2 = 0.5 * ((1 - beta) * v1 + (1 + beta) * v2);
        return new double[]{child1, child2};
    }

}
