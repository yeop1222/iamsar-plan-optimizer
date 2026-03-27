package papervalidation.service.ga;

import papervalidation.domain.ga.Chromosome;

import java.util.List;
import java.util.Random;

/**
 * Tournament selection (k=tournamentSize).
 */
public class SelectionOperator {

    private final int tournamentSize;
    private final Random random;

    public SelectionOperator(int tournamentSize, Random random) {
        this.tournamentSize = tournamentSize;
        this.random = random;
    }

    public Chromosome select(List<Chromosome> population) {
        Chromosome best = null;
        for (int i = 0; i < tournamentSize; i++) {
            Chromosome candidate = population.get(random.nextInt(population.size()));
            if (best == null || candidate.getFitness() > best.getFitness()) {
                best = candidate;
            }
        }
        return best;
    }
}
