package uk.ac.ebi.protvar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.ac.ebi.protvar.types.IdentifierType;

/**
 * A single typed biological identifier submitted as part of a browse request.
 *
 * <p><b>Type resolution contract:</b>
 * <ul>
 *   <li>If {@code type} is provided, it is trusted as-is (no further detection).</li>
 *   <li>If {@code type} is {@code null}, the backend will auto-detect it from {@code value}
 *       via {@code InputTypeResolver}. Falls back to {@code GENE} if the value is ambiguous.</li>
 * </ul>
 *
 * <p>Only biological identifiers (UniProt, Gene, PDB, Ensembl, RefSeq) are valid here.
 * Variant queries use the GET {@code /mapping?input=} endpoint; uploaded results use {@code resultId}.
 */
@Schema(description = "A typed biological identifier for browse queries. 'type' is optional — if omitted, auto-detected from 'value'.")
public record Identifier(
    @Schema(description = "Identifier type. If null, auto-detected from 'value' by InputTypeResolver.", example = "GENE", nullable = true)
    IdentifierType type,

    @Schema(description = "Identifier value (UniProt accession, gene symbol, PDB ID, Ensembl ID, or RefSeq accession).", example = "BRCA2")
    String value
) {}
