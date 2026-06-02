package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Public-facing status payload for {@code GET /status}. Each top-level service
 * field is {@code "up"}, {@code "down"}, or {@code "unknown"}. Internal
 * details from Actuator's health indicators are intentionally not surfaced.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusResponse(
        String api,
        String db,
        String queue,
        String cache,
        String mcp,
        Map<String, String> embeddings,
        Instant checked
) {}
