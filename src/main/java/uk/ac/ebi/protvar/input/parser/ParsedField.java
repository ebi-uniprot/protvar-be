package uk.ac.ebi.protvar.input.parser;

public interface ParsedField {
    String CHROMOSOME = "chromosome";
    String POSITION = "position";
    String REF_ALLELE = "ref";
    String ALT_ALLELE = "alt";
    String ACCESSION = "accession";
    String AMINO_ACID_REF = "aa_ref";
    String AMINO_ACID_ALT = "aa_alt";
    String VARIANT_ID = "variant_id";
    String GENE_SYMBOL = "gene_symbol";
    // add more as needed

    // HGVSg
    String RS_37 = "rs37";

    // HGVSp+c
    String RS_ACC = "rsAcc";

    // HGVSc
    String POS = "pos";
    String REF = "ref";
    String ALT = "alt";

    // HGVSc optional
    String GENE = "gene";
    String PROT_REF = "protRef";
    String PROT_ALT = "protAlt";
    String PROT_POS = "protPos";
}