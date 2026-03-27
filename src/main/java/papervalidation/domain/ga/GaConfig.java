package papervalidation.domain.ga;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GaConfig {
    private int populationSize = 100;
    private int maxGenerations = 500;
    private double crossoverRate = 0.8;
    private double mutationRate = 0.05;
    private int tournamentSize = 5;
    private int elitismCount = 2;
    private int convergenceGenerations = 50;
}
