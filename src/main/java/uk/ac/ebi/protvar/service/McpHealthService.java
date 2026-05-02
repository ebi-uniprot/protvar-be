package uk.ac.ebi.protvar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Pings protvar-mcp's actuator health endpoint. Used by the public status
 * endpoint. Not enabled by default in non-cluster envs — failures are
 * tolerated quietly (logged at debug only).
 */
@Slf4j
@Service
public class McpHealthService {

    private final RestTemplate restTemplate;
    private final String mcpBaseUrl;

    public McpHealthService(@Value("${protvar-mcp.url}") String mcpBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.mcpBaseUrl = mcpBaseUrl;
    }

    public boolean isUp() {
        try {
            restTemplate.getForObject(mcpBaseUrl + "/actuator/health", String.class);
            return true;
        } catch (Exception e) {
            log.debug("MCP health check failed: {}", e.getMessage());
            return false;
        }
    }
}
