package papervalidation.service.raster;

import papervalidation.domain.datum.DatumSet;
import papervalidation.domain.raster.SamplePoint;

import java.util.List;

/**
 * Sampling strategy interface for different datum types.
 */
public interface SamplingStrategy {
    List<SamplePoint> generateSamples(DatumSet datumSet);
}
