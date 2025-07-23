package uk.ac.ebi.protvar.input;

public enum VariantType {
    GENOMIC("genomic"),
    CODING_DNA("coding DNA"),
    PROTEIN("protein"),
    VARIANT_ID("variant ID"),

    INVALID("invalid");

    private String name;

    VariantType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}