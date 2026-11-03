package uk.ac.ebi.protvar.model.data;

/** A UniProt humsavar variant: VAR_ ID → accession, position, wild-type and variant amino acid (1-letter). */
public record Humsavar(String varId, String accession, int position, String wtAa, String mtAa) {
}
