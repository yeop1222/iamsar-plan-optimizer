package papervalidation.domain.sru;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentCondition {
    private double visibility = 15.0;   // NM (1~20)
    private double windSpeed = 3.0;     // kts
    private double waveHeight = 0.5;    // meters
}
