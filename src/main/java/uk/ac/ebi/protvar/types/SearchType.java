package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Type of search term for querying variants")
public enum SearchType {

    // Direct variant input
    @Schema(description = "A single variant in any supported format (genomic, protein, transcript, coding)")
    VARIANT("variant", false),

    @Schema(description = "A 32-character identifier referencing a previously submitted list of variants")
    INPUT_ID("input_id", false),

    // Biological identifiers
    @Schema(description = "UniProt accession (e.g., P12345)")
    UNIPROT("uniprot", true),

    @Schema(description = "Gene symbol (e.g., BRCA1)")
    GENE("gene", true),

    @Schema(description = "Ensembl identifier (ENSG, ENST, ENSP, ENSE)")
    ENSEMBL("ensembl", true),

    @Schema(description = "PDB structure ID (e.g., 1ABC)")
    PDB("pdb", true),

    @Schema(description = "RefSeq identifier (e.g., NM_000546)")
    REFSEQ("refseq", true);

    @JsonValue
    private final String value;
    private final boolean identifier;

    /**
     * Safe parsing that returns null on invalid input
     */
    public static SearchType parseOrNull(Object input) {
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a SearchType from string (case-insensitive).
     */
    public static SearchType parse(Object input) {
        if (input == null) return null;

        String str = input.toString().trim();
        for (SearchType type : values()) {
            if (type.value.equalsIgnoreCase(str)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid search type: " + input);
    }

    public static SearchType parse(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        for (SearchType c : values()) {
            if (c.name().equalsIgnoreCase(trimmed)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Invalid search type: " + value);
    }

    @JsonCreator
    public static SearchType fromJson(Object input) {
        return parse(input);
    }
}
