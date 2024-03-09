package uk.ac.ebi.protvar.input;

public enum Type {
    GENOMIC,
    CODING, // coding DNA
    PROTEIN,
    ID;   // variant ID e.g. DBSNP, ClinVar or COSMIC IDs
}