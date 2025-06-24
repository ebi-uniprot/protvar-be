package uk.ac.ebi.protvar.input;

public enum Type {
    GENOMIC("genomic"),
    CODING("coding"), // coding DNA
    PROTEIN("protein"),
    // todo: rename to VARIANT_ID?
    ID("ID"),   // variant ID e.g. DBSNP, ClinVar or COSMIC IDs

    INVALID("invalid");

    private String name;

    Type(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}