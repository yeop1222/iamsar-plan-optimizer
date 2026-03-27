package papervalidation.service.raster;

import papervalidation.domain.datum.DatumPoint;
import papervalidation.domain.datum.DatumSet;
import papervalidation.domain.raster.SamplePoint;
import papervalidation.util.CoordinateConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Area datum: raster-scan inside polygon at 200m grid.
 * Ray-casting for point-in-polygon test.
 */
public class AreaSamplingStrategy implements SamplingStrategy {

    private static final double CEP_COEFF = 1.17741;
    private static final double NM_TO_M = 1852.0;
    private static final double GRID_SIZE = 200.0; // meters

    @Override
    public List<SamplePoint> generateSamples(DatumSet datumSet) {
        List<DatumPoint> points = datumSet.getPoints();
        if (points.size() < 3) {
            return new PointSamplingStrategy().generateSamples(datumSet);
        }

        // Convert polygon to EPSG:3857
        double[][] polyMerc = new double[points.size()][2];
        double avgError = 0;
        double avgLat = 0;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            DatumPoint p = points.get(i);
            double[] mc = CoordinateConverter.toMercator(p.getLongitude(), p.getLatitude());
            polyMerc[i] = mc;
            avgError += p.getErrorNm();
            avgLat += p.getLatitude();
            minX = Math.min(minX, mc[0]);
            maxX = Math.max(maxX, mc[0]);
            minY = Math.min(minY, mc[1]);
            maxY = Math.max(maxY, mc[1]);
        }
        avgError /= points.size();
        avgLat /= points.size();

        double sigmaBase = avgError * NM_TO_M / CEP_COEFF;
        double sf = CoordinateConverter.mercatorScaleFactor(avgLat);
        double sigmaMerc = sigmaBase * sf;

        // Raster scan
        List<SamplePoint> samples = new ArrayList<>();
        for (double y = minY; y <= maxY; y += GRID_SIZE) {
            for (double x = minX; x <= maxX; x += GRID_SIZE) {
                if (isInsidePolygon(x, y, polyMerc)) {
                    samples.add(new SamplePoint(x, y, sigmaMerc, 1.0)); // weight set later
                }
            }
        }

        // Normalize weights
        if (!samples.isEmpty()) {
            double w = 1.0 / samples.size();
            for (SamplePoint sp : samples) {
                sp.setWeight(w);
            }
        }

        return samples;
    }

    /**
     * Ray-casting point-in-polygon test.
     */
    private boolean isInsidePolygon(double x, double y, double[][] poly) {
        boolean inside = false;
        int n = poly.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly[i][0], yi = poly[i][1];
            double xj = poly[j][0], yj = poly[j][1];
            if ((yi > y) != (yj > y) &&
                    x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }
}
