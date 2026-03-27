package papervalidation.service.raster;

import papervalidation.domain.datum.DatumPoint;
import papervalidation.domain.datum.DatumSet;
import papervalidation.domain.raster.SamplePoint;
import papervalidation.util.CoordinateConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Point datum: each datum point becomes a sample point.
 * weight = 1/N, sigma = E * 1852 / 1.17741 * Mercator SF
 */
public class PointSamplingStrategy implements SamplingStrategy {

    private static final double CEP_COEFF = 1.17741; // sqrt(2 * ln(2))
    private static final double NM_TO_M = 1852.0;

    @Override
    public List<SamplePoint> generateSamples(DatumSet datumSet) {
        List<DatumPoint> points = datumSet.getPoints();
        int n = points.size();
        double weight = 1.0 / n;
        List<SamplePoint> samples = new ArrayList<>();

        for (DatumPoint dp : points) {
            double[] mc = CoordinateConverter.toMercator(dp.getLongitude(), dp.getLatitude());
            double sigmaBase = dp.getErrorNm() * NM_TO_M / CEP_COEFF;
            double sf = CoordinateConverter.mercatorScaleFactor(dp.getLatitude());
            double sigmaMerc = sigmaBase * sf;

            samples.add(new SamplePoint(mc[0], mc[1], sigmaMerc, weight));
        }
        return samples;
    }
}
