package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Biological identifier types for browse queries (UniProt, Gene, PDB, Ensembl, RefSeq).
 * Variant queries and uploaded result IDs are handled separately via the {@code q}
 * and {@code resultId} fields on {@code MappingRequest}.
 */
@Getter
@RequiredArgsConstructor
@Schema(description = "Biological identifier type for browse queries: uniprot, gene, pdb, ensembl, or refseq.")
public enum IdentifierType {
    UNIPROT("uniprot"),
    GENE("gene"),
    PDB("pdb"),
    ENSEMBL("ensembl"),
    REFSEQ("refseq");

    @JsonValue
    private final String value;

    @JsonCreator
    public static IdentifierType fromString(String value) {
        if (value == null) return null;
        for (IdentifierType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown IdentifierType: " + value);
    }
}
