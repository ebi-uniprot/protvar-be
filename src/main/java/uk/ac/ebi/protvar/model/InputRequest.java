package uk.ac.ebi.protvar.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InputRequest {
    // Unique identifier for the input. Will be null on the initial request.
    // Returned as part of the response and used in subsequent requests to reference cached input.
    String inputId;

    // The raw input data (e.g., text or file content).
    // Provided only in the initial request; omitted in subsequent requests, where it's loaded from cache using inputId.
    String rawInput;

    // Genome assembly to use (e.g., GRCh37, GRCh38, AUTO).
    // May vary between requests, even for the same inputId, to allow reprocessing using different assemblies.
    String assembly;

    public boolean isAutoDetectBuild() {
        return assembly != null && assembly.equalsIgnoreCase("AUTO");
    }
}
