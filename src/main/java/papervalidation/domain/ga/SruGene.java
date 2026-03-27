package papervalidation.domain.ga;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 6-dimensional gene for one SRU's search area.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SruGene {
    private String sruId;
    private double centerX;       // EPSG:3857
    private double centerY;       // EPSG:3857
    private double width;         // meters
    private double height;        // meters
    private double rotation;      // degrees [0, 360)
    private int altitudeIndex;    // 0, 1, 2 for aircraft; 0 for ship

    public SruGene copy() {
        return new SruGene(sruId, centerX, centerY, width, height, rotation, altitudeIndex);
    }
}
