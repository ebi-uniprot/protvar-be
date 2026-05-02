package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.protvar.model.response.StatusResponse;
import uk.ac.ebi.protvar.service.EmbeddingHealthService;
import uk.ac.ebi.protvar.service.McpHealthService;

import java.time.Instant;

/**
 * Public service status. Reads Actuator's auto-registered health indicators
 * for db / redis / rabbit, pings each embedding model directly, and pings
 * protvar-mcp's actuator health. The payload is intentionally minimal —
 * internal driver/host/port details from Actuator are not exposed.
 */
@Tag(name = "Status")
@RestController
@CrossOrigin
@RequestMapping("/status")
@RequiredArgsConstructor
public class StatusController {

    private final HealthEndpoint healthEndpoint;
    private final EmbeddingHealthService embeddingHealth;
    private final McpHealthService mcpHealth;

    @GetMapping
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(new StatusResponse(
                "up",
                componentStatus("db"),
                componentStatus("rabbit"),
                componentStatus("redis"),
                mcpHealth.isUp() ? "up" : "down",
                embeddingHealth.checkAll(),
                Instant.now()
        ));
    }

    private String componentStatus(String name) {
        var component = healthEndpoint.healthForPath(name);
        if (component == null) return "unknown";
        return Status.UP.equals(component.getStatus()) ? "up" : "down";
    }
}
