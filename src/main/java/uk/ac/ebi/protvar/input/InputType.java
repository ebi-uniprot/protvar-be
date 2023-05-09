package uk.ac.ebi.protvar.input;

public enum InputType {
    GEN, // genomic input
    PRO, // protein input
    // protein->genomic coords mapping needed
    RS;   // variant ID input
    // id->genomic coords mapping needed - dbSNP RS ID e.g. rs864622779

    public enum Gen {
        VCF,    // genomic coords in VCF format
        HGVS,   // genomic coords in HGVS format
        GNOMAD  // genomic coords in gnoMAD format - gnomAD Variant ID e.g. 14-90429394-T-C
    }
}