package uk.ac.ebi.protvar.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import uk.ac.ebi.protvar.types.SearchType;

@Data
@Schema(description = "A search term with optional type specification")
public class SearchTerm {

    @Schema(
            description = """
            The search value. Can be:
            - A variant in any supported format (e.g., 'chr1:123456A>G', 'P12345:p.Ala123Val')
            - An input ID (32-character identifier)
            - A biological identifier (e.g., 'P12345', 'BRCA1', 'ENSG00000012048', '1ABC', 'NM_000546')
            """,
            example = "P12345"
    )
    @NotBlank(message = "Search term value must not be blank")
    private String value;

    @Schema(
            description = """
            The type of search term. If not specified, the system will attempt automatic detection.
            - variant: A single variant in any supported format
            - input_id: Reference to a previously submitted variant list
            - uniprot: UniProt accession
            - gene: Gene symbol
            - ensembl: Ensembl identifier (ENSG/ENST/ENSP/ENSE)
            - pdb: PDB structure ID
            - refseq: RefSeq identifier
            """,
            example = "uniprot"
    )
    private SearchType type;

    // For deserialization
    public SearchTerm() {}

    @JsonCreator
    public SearchTerm(
            @JsonProperty("value") String value,
            @JsonProperty("type") SearchType type
    ) {
        this.value = value;
        this.type = type;
    }

    // Convenience constructor for identifier terms
    public static SearchTerm identifier(String value, SearchType type) {
        if (!type.isIdentifier()) {
            throw new IllegalArgumentException("Type must be an identifier type");
        }
        return new SearchTerm(value, type);
    }

    // Convenience constructor for variant
    public static SearchTerm variant(String value) {
        return new SearchTerm(value, SearchType.VARIANT);
    }

    // Convenience constructor for input ID
    public static SearchTerm inputId(String value) {
        return new SearchTerm(value, SearchType.INPUT_ID);
    }
}
