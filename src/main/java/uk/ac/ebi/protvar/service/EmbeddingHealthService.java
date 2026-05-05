package uk.ac.ebi.protvar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.protvar.config.ModelRegistryProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pings each enabled embedding-service model on its {@code /health} endpoint
 * and returns a per-model up/down map for the public status endpoint.
 *
 * <p>Failures (timeout, connect refused, non-2xx) are logged at debug level
 * only — the status endpoint is allowed to report "down" without spamming
 * application logs every poll.
 */
@Slf4j
@Service
public class EmbeddingHealthService {

    private final RestTemplate restTemplate;
    private final ModelRegistryProperties modelRegistry;

    public EmbeddingHealthService(@Qualifier("embeddingRestTemplate") RestTemplate restTemplate,
                                  ModelRegistryProperties modelRegistry) {
        this.restTemplate = restTemplate;
        this.modelRegistry = modelRegistry;
    }

    /** Returns enabled-model id → {@code "up"} or {@code "down"}. Order preserved. */
    public Map<String, String> checkAll() {
        Map<String, String> result = new LinkedHashMap<>();
        modelRegistry.getModels().forEach((id, cfg) -> {
            if (!cfg.isEnabled()) return;
            result.put(id, ping(id) ? "up" : "down");
        });
        return result;
    }

    private boolean ping(String modelId) {
        String url = modelRegistry.resolveServiceUrl(modelId) + "/health";
        try {
            restTemplate.getForObject(url, String.class);
            return true;
        } catch (Exception e) {
            log.debug("Embedding model {} health check failed: {}", modelId, e.getMessage());
            return false;
        }
    }
}
