package papervalidation.domain.sru;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sru {
    private String id;
    private String name;
    private SruType type;
    private double searchSpeed;         // kts (V)
    private double endurance;           // hours (T)
    private int altitude;               // meters (surface=0)
    private boolean fatigue;
    private Double correctedSweepWidth; // null=auto, value=manual override

    // Calculation results (read-only in API response)
    private double uncorrectedSweepWidth;   // W_0
    private double weatherFactor;           // f_w
    private double velocityFactor;          // f_v
    private double fatigueFactor;           // f_f
    private double calculatedSweepWidth;    // W_0 * f_v * f_w * f_f
    private double searchEffort;            // Z = W_corr * V * T

    /** Effective W_corr: manual override if set, otherwise calculated */
    public double getEffectiveSweepWidth() {
        return (correctedSweepWidth != null) ? correctedSweepWidth : calculatedSweepWidth;
    }
}
