package papervalidation.domain.datum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatumPoint {
    private double latitude;
    private double longitude;
    private double errorNm;
}
