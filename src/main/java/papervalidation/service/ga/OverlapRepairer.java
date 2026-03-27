package papervalidation.service.ga;

import papervalidation.domain.ga.Chromosome;
import papervalidation.domain.ga.SruGene;
import papervalidation.domain.raster.PocRaster;

/**
 * Repairs overlapping search areas by pushing them apart.
 * Same-altitude SRUs with 200m safety buffer.
 */
public class OverlapRepairer {

    private static final double BUFFER = 200.0;
    private static final int MAX_OUTER = 50;
    private static final int MAX_INNER = 10;

    public void repair(Chromosome chromosome, PocRaster raster) {
        SruGene[] genes = chromosome.getGenes();
        if (genes.length < 2) return;

        for (int outer = 0; outer < MAX_OUTER; outer++) {
            boolean anyOverlap = false;

            for (int i = 0; i < genes.length; i++) {
                for (int j = i + 1; j < genes.length; j++) {
                    if (genes[i].getAltitudeIndex() != genes[j].getAltitudeIndex()) continue;

                    for (int inner = 0; inner < MAX_INNER; inner++) {
                        double overlap = getOverlapDepth(genes[i], genes[j]);
                        if (overlap <= 0) break;

                        anyOverlap = true;
                        pushApart(genes[i], genes[j], overlap);
                    }
                }
            }

            // Clamp to raster bounds
            for (SruGene gene : genes) {
                gene.setCenterX(clamp(gene.getCenterX(), raster.getOriginX(), raster.getMaxX()));
                gene.setCenterY(clamp(gene.getCenterY(), raster.getOriginY(), raster.getMaxY()));
            }

            if (!anyOverlap) break;
        }
    }

    private double getOverlapDepth(SruGene g1, SruGene g2) {
        double hw1 = g1.getWidth() / 2 + BUFFER;
        double hh1 = g1.getHeight() / 2 + BUFFER;
        double hw2 = g2.getWidth() / 2 + BUFFER;
        double hh2 = g2.getHeight() / 2 + BUFFER;

        double overlapX = Math.min(g1.getCenterX() + hw1, g2.getCenterX() + hw2)
                - Math.max(g1.getCenterX() - hw1, g2.getCenterX() - hw2);
        double overlapY = Math.min(g1.getCenterY() + hh1, g2.getCenterY() + hh2)
                - Math.max(g1.getCenterY() - hh1, g2.getCenterY() - hh2);

        if (overlapX <= 0 || overlapY <= 0) return 0;
        return Math.min(overlapX, overlapY);
    }

    private void pushApart(SruGene g1, SruGene g2, double depth) {
        double dx = g2.getCenterX() - g1.getCenterX();
        double dy = g2.getCenterY() - g1.getCenterY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1) { // near-coincident: push in arbitrary direction
            dx = 1;
            dy = 0;
            dist = 1;
        }

        double push = depth * 0.5;
        double nx = dx / dist;
        double ny = dy / dist;

        g1.setCenterX(g1.getCenterX() - nx * push);
        g1.setCenterY(g1.getCenterY() - ny * push);
        g2.setCenterX(g2.getCenterX() + nx * push);
        g2.setCenterY(g2.getCenterY() + ny * push);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
