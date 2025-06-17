package uk.ac.ebi.protvar.types;

// rename to SearchType(?)
// ?query=BRCA1&type=GENE

// linking directly to a single variant
// /api/variant?[format=GENOMIC]&variant=chr1-123456-A-T
// /api/variant?format=PROTEIN&variant=P12345-123-A-V
// /api/variant?format=VARIANT_ID&variant=rs123456
public enum IdentifierType {
    ENSEMBL, UNIPROT, PDB, REFSEQ, CUSTOM_INPUT, GENE
    /*
    USER_INPUT, // user-submitted list of variants, either a userInputId, or the raw input itself (incl. single input from direct link?)
    UNIPROT, // UniProt accession
    PDB, // PDB structure ID
    REFSEQ, // RefSeq identifier
    GENE, // Gene symbol
    FREE_TEXT, // used for semantic search
     */
}
