package papervalidation.service.searchplan;

import org.springframework.stereotype.Component;
import papervalidation.domain.raster.PocRaster;
import papervalidation.domain.searchplan.SearchArea;
import papervalidation.domain.sru.Sru;
import papervalidation.util.CoordinateConverter;

import java.util.*;

/**
 * Common POS/POD/POC calculation engine.
 */
@Component
public class PosCalculator {

    /** POD from coverage factor */
    public double calculatePod(double coverageFactor) {
        return 1.0 - Math.exp(-coverageFactor);
    }

    /** Coverage Factor = Z / A */
    public double calculateCoverageFactor(Sru sru, double areaNm2) {
        double Z = sru.getEffectiveSweepWidth() * sru.getSearchSpeed() * sru.getEndurance();
        return (areaNm2 > 0) ? Z / areaNm2 : 0;
    }

    /** Sum POC inside a rotated rectangle */
    public double sumPocInRect(PocRaster raster, SearchArea area) {
        double cx = area.getCenterX();
        double cy = area.getCenterY();
        double hw = area.getWidth() / 2.0;
        double hh = area.getHeight() / 2.0;
        double rot = Math.toRadians(area.getRotation());
        double cosR = Math.cos(-rot);
        double sinR = Math.sin(-rot);

        // AABB of rotated rect
        double absW = hw * Math.abs(Math.cos(rot)) + hh * Math.abs(Math.sin(rot));
        double absH = hw * Math.abs(Math.sin(rot)) + hh * Math.abs(Math.cos(rot));

        int colMin = Math.max(0, (int) ((cx - absW - raster.getOriginX()) / raster.getCellSize()));
        int colMax = Math.min(raster.getCols() - 1, (int) ((cx + absW - raster.getOriginX()) / raster.getCellSize()));
        int rowMin = Math.max(0, (int) ((cy - absH - raster.getOriginY()) / raster.getCellSize()));
        int rowMax = Math.min(raster.getRows() - 1, (int) ((cy + absH - raster.getOriginY()) / raster.getCellSize()));

        double sum = 0;
        for (int r = rowMin; r <= rowMax; r++) {
            double cellY = raster.getCellCenterY(r);
            for (int c = colMin; c <= colMax; c++) {
                double cellX = raster.getCellCenterX(c);
                // Transform to local rect coordinates
                double dx = cellX - cx;
                double dy = cellY - cy;
                double lx = dx * cosR - dy * sinR;
                double ly = dx * sinR + dy * cosR;

                if (Math.abs(lx) <= hw && Math.abs(ly) <= hh) {
                    sum += raster.getData()[r][c];
                }
            }
        }
        return sum;
    }

    /** Calculate area in NM² with Web Mercator correction */
    public double calculateAreaNm2(SearchArea area, double latDeg) {
        double cosFactor = Math.cos(Math.toRadians(latDeg));
        double widthNm = area.getWidth() * cosFactor / 1852.0;
        double heightNm = area.getHeight() / 1852.0;
        return widthNm * heightNm;
    }

    /**
     * Total POS with multi-altitude synergy.
     * For each active cell:
     *   1. Find covering SRUs and their PODs
     *   2. Same altitude group -> max POD only
     *   3. Different altitude groups -> independent: POD_combined = 1 - Π(1-POD_i)
     *   4. POS(cell) = POC(cell) * POD_combined(cell)
     *   5. totalPOS = Σ POS(cell)
     */
    public double calculateTotalPos(PocRaster raster, List<SearchArea> areas, List<Sru> srus) {
        int rows = raster.getRows();
        int cols = raster.getCols();
        double[][] pocData = raster.getData();

        // Precompute per-SRU: coverage factor and altitude
        int nSru = areas.size();
        double[] coverageFactors = new double[nSru];
        int[] altitudes = new int[nSru];

        for (int s = 0; s < nSru; s++) {
            SearchArea sa = areas.get(s);
            Sru sru = findSru(srus, sa.getSruId());
            if (sru == null) continue;

            double centerLat = CoordinateConverter.yToLat(sa.getCenterY());
            double areaNm2 = calculateAreaNm2(sa, centerLat);
            coverageFactors[s] = calculateCoverageFactor(sru, areaNm2);
            altitudes[s] = sru.getAltitude();
        }

        double totalPos = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double poc = pocData[r][c];
                if (poc <= 0) continue;

                double cellX = raster.getCellCenterX(c);
                double cellY = raster.getCellCenterY(r);

                // Find which SRUs cover this cell
                Map<Integer, Double> altMaxPod = new HashMap<>();
                for (int s = 0; s < nSru; s++) {
                    if (isInsideArea(cellX, cellY, areas.get(s))) {
                        double pod = calculatePod(coverageFactors[s]);
                        int alt = altitudes[s];
                        altMaxPod.merge(alt, pod, Math::max);
                    }
                }

                if (altMaxPod.isEmpty()) continue;

                // Multi-altitude synergy
                double podCombined = 1.0;
                for (double maxPod : altMaxPod.values()) {
                    podCombined *= (1.0 - maxPod);
                }
                podCombined = 1.0 - podCombined;

                totalPos += poc * podCombined;
            }
        }
        return totalPos;
    }

    private boolean isInsideArea(double x, double y, SearchArea area) {
        double dx = x - area.getCenterX();
        double dy = y - area.getCenterY();
        double rot = Math.toRadians(-area.getRotation());
        double cosR = Math.cos(rot);
        double sinR = Math.sin(rot);
        double lx = dx * cosR - dy * sinR;
        double ly = dx * sinR + dy * cosR;
        return Math.abs(lx) <= area.getWidth() / 2.0 && Math.abs(ly) <= area.getHeight() / 2.0;
    }

    private Sru findSru(List<Sru> srus, String sruId) {
        return srus.stream().filter(s -> s.getId().equals(sruId)).findFirst().orElse(null);
    }
}
