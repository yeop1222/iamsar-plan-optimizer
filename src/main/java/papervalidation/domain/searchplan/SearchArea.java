package papervalidation.domain.searchplan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchArea {
    private String sruId;
    private double centerX;    // EPSG:3857
    private double centerY;    // EPSG:3857
    private double width;      // meters (EPSG:3857)
    private double height;     // meters (EPSG:3857)
    private double rotation;   // degrees
}
