package uk.ac.ebi.protvar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.ac.ebi.protvar.types.InputType;

/**
 * A single typed identifier submitted as part of a multi-identifier browse request.
 *
 * <p><b>Type resolution contract:</b>
 * <ul>
 *   <li>If {@code type} is provided, it is trusted as-is (no further detection).</li>
 *   <li>If {@code type} is {@code null}, the backend will auto-detect it from {@code value}
 *       using {@code InputTypeResolver.resolve()}. Falls back to {@code GENE} if the value
 *       is ambiguous (e.g. a short gene symbol that could match multiple patterns).</li>
 * </ul>
 *
 * <p>This mirrors the frontend convention where bare URL params (e.g. {@code ?id=P22304})
 * are auto-detected by {@code parseIdParam()} in {@code InputTypeResolver.ts}.
 * Callers that know the type should always supply it to avoid misdetection.
 */
@Schema(description = "A typed identifier for browse queries. 'type' is optional — if omitted, the backend auto-detects it from 'value'.")
public record IdInput(
    @Schema(description = "Identifier type. If null, auto-detected from 'value' by InputTypeResolver.", example = "GENE", nullable = true)
    InputType type,

    @Schema(description = "Identifier value (UniProt accession, gene symbol, PDB ID, Ensembl ID, or RefSeq accession).", example = "BRCA2")
    String value
) {}
