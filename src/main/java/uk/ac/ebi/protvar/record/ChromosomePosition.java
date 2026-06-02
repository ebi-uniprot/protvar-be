package uk.ac.ebi.protvar.record;

import java.util.Objects;

// Basic record with automatic equals() and hashCode() for deduplication
// Customised here for case-insensitive comparison of chromosome
// Set<ChromosomePosition> will deduplicate based on both fields
public record ChromosomePosition(String chromosome, Integer position) {

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChromosomePosition that = (ChromosomePosition) obj;

        // Case-insensitive comparison for chromosome
        return Objects.equals(position, that.position) &&
                (chromosome != null ? chromosome.equalsIgnoreCase(that.chromosome)
                        : that.chromosome == null);
    }

    @Override
    public int hashCode() {
        // Use lowercase for consistent hashing if case-insensitive
        return Objects.hash(chromosome != null ? chromosome.toLowerCase() : null, position);
    }
}