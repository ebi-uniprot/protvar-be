package uk.ac.ebi.protvar.types;

// rename to SearchType(?)
// ?query=BRCA1&type=GENE

import io.swagger.v3.oas.annotations.media.Schema;

// linking directly to a single variant
// /api/variant?[format=GENOMIC]&variant=chr1-123456-A-T
// /api/variant?format=PROTEIN&variant=P12345-123-A-V
// /api/variant?format=VARIANT_ID&variant=rs123456


// todo add INVALID/UNKNOWN?
@Schema(description = "The input type.")
public enum InputType {
    INPUT_ID, // 32-char long user input ID
    ENSEMBL, // Ensembl identifier (ENSG, ENST, ENSP, ENSE)
    UNIPROT, // UniProt accessio
    PDB,     // PDB structure ID
    REFSEQ,  // RefSeq identifier
    GENE,    // Gene symbol
    SINGLE_VARIANT // single variant input in any of the supported format
    // FREE_TEXT, // used for semantic search(?)
}
