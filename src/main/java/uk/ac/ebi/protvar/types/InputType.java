package uk.ac.ebi.protvar.types;

// rename to SearchType(?)
// ?query=BRCA1&type=GENE

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// linking directly to a single variant
// /api/variant?[format=GENOMIC]&variant=chr1-123456-A-T
// /api/variant?format=PROTEIN&variant=P12345-123-A-V
// /api/variant?format=VARIANT_ID&variant=rs123456


// todo add INVALID/UNKNOWN?
@Getter
@RequiredArgsConstructor
@Schema(description = "The input type.")
public enum InputType { // todo: rename to Query/SearchType??
    // Variant inputs
    VARIANT("variant"), // variant input in any supported format
    INPUT_ID("input_id"), // 32-char long user input id
    // Identifiers
    ENSEMBL("ensembl"), // Ensembl identifier (ENSG, ENST, ENSP, ENSE)
    UNIPROT("uniprot"), // UniProt accessio
    PDB("pdb"),     // PDB structure ID
    REFSEQ("refseq"),  // RefSeq identifier
    GENE("gene");    // Gene symbol
    // FREE_TEXT, // to be used for semantic search(?)

    @JsonValue // This ensures JSON serialization uses the lowercase value
    private final String value;
}
