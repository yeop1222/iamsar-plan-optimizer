package papervalidation.domain.raster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raster sampling point in EPSG:3857 coordinates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamplePoint {
    private double x;       // EPSG:3857 X
    private double y;       // EPSG:3857 Y
    private double sigma;   // standard deviation in meters (Mercator-corrected)
    private double weight;  // probability weight
}
