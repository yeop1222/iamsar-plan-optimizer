package papervalidation.domain.ga;

import lombok.Data;

@Data
public class Chromosome implements Comparable<Chromosome> {
    private SruGene[] genes;
    private double fitness;

    public Chromosome(int nSru) {
        genes = new SruGene[nSru];
    }

    public Chromosome copy() {
        Chromosome c = new Chromosome(genes.length);
        for (int i = 0; i < genes.length; i++) {
            c.genes[i] = genes[i].copy();
        }
        c.fitness = this.fitness;
        return c;
    }

    @Override
    public int compareTo(Chromosome other) {
        return Double.compare(other.fitness, this.fitness); // descending
    }
}
