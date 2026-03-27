package papervalidation.service.raster;

import papervalidation.domain.datum.DatumPoint;
import papervalidation.domain.datum.DatumSet;
import papervalidation.domain.raster.SamplePoint;
import papervalidation.util.CoordinateConverter;
import papervalidation.util.VincentyUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Line datum: sample along line segments between consecutive waypoints.
 * Interpolate sigma between endpoints.
 */
public class LineSamplingStrategy implements SamplingStrategy {

    private static final double CEP_COEFF = 1.17741;
    private static final double NM_TO_M = 1852.0;
    private static final double SAMPLE_INTERVAL = 50.0; // meters

    @Override
    public List<SamplePoint> generateSamples(DatumSet datumSet) {
        List<DatumPoint> points = datumSet.getPoints();
        if (points.size() < 2) {
            return new PointSamplingStrategy().generateSamples(datumSet);
        }

        // Calculate total path length
        double totalLength = 0;
        double[] segLengths = new double[points.size() - 1];
        for (int i = 0; i < points.size() - 1; i++) {
            DatumPoint p1 = points.get(i);
            DatumPoint p2 = points.get(i + 1);
            segLengths[i] = VincentyUtils.distance(
                    p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude());
            totalLength += segLengths[i];
        }

        List<SamplePoint> samples = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            DatumPoint p1 = points.get(i);
            DatumPoint p2 = points.get(i + 1);
            double segLen = segLengths[i];
            int nSamples = Math.max(1, (int) Math.round(segLen / SAMPLE_INTERVAL));
            double segWeight = (totalLength > 0) ? segLen / totalLength : 1.0 / (points.size() - 1);
            double sampleWeight = segWeight / nSamples;

            double[] mc1 = CoordinateConverter.toMercator(p1.getLongitude(), p1.getLatitude());
            double[] mc2 = CoordinateConverter.toMercator(p2.getLongitude(), p2.getLatitude());
            double sigma1 = p1.getErrorNm() * NM_TO_M / CEP_COEFF;
            double sigma2 = p2.getErrorNm() * NM_TO_M / CEP_COEFF;

            for (int j = 0; j < nSamples; j++) {
                double t = (nSamples == 1) ? 0.5 : (double) j / (nSamples - 1);
                double x = mc1[0] + t * (mc2[0] - mc1[0]);
                double y = mc1[1] + t * (mc2[1] - mc1[1]);
                double sigmaBase = sigma1 + t * (sigma2 - sigma1);

                // Mercator scale factor at interpolated latitude
                double lat = CoordinateConverter.yToLat(y);
                double sf = CoordinateConverter.mercatorScaleFactor(lat);
                double sigmaMerc = sigmaBase * sf;

                samples.add(new SamplePoint(x, y, sigmaMerc, sampleWeight));
            }
        }
        return samples;
    }
}
