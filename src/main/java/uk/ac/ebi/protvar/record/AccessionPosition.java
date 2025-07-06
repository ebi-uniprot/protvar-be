package uk.ac.ebi.protvar.record;

import java.util.Objects;

// Basic record with automatic equals() and hashCode() for deduplication
// Customised here for case-insensitive comparison of accession
// Set<AccessionPosition> will deduplicate based on both fields
public record AccessionPosition(String accession, Integer position) {

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AccessionPosition that = (AccessionPosition) obj;

        // Case-insensitive comparison for accession
        return Objects.equals(position, that.position) &&
                (accession != null ? accession.equalsIgnoreCase(that.accession)
                        : that.accession == null);
    }

    @Override
    public int hashCode() {
        // Use lowercase for consistent hashing if case-insensitive
        return Objects.hash(accession != null ? accession.toLowerCase() : null, position);
    }

}