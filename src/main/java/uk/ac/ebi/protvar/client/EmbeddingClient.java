package uk.ac.ebi.protvar.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * External API calls to embedding service.
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final RestTemplate restTemplate;

    public EmbeddingClient(@Qualifier("embeddingRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get embedding vector from external service.
     * @param queryText Text to generate embedding for
     * @return Optional containing embedding array, empty if service unavailable
     */
    public Optional<float[]> getEmbedding(String queryText) {
        try {
            Map<String, String> requestBody = Map.of("text", queryText);

            @SuppressWarnings("unchecked")
            Map<String, List<Number>> response = restTemplate.postForObject(
                    "/embed",
                    requestBody,
                    Map.class
            );

            if (response == null || response.get("embedding") == null) {
                log.warn("Embedding service returned null response for text: {}", queryText);
                return Optional.empty();
            }

            List<Number> embedding = response.get("embedding");

            float[] floatArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                floatArray[i] = embedding.get(i).floatValue();
            }
            return Optional.of(floatArray);

        } catch (RestClientException e) {
            log.error("Failed to get embedding from service for text: {}. Error: {}",
                    queryText, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error getting embedding for text: {}", queryText, e);
            return Optional.empty();
        }
    }

    /**
     * Check if embedding service is healthy.
     * @return true if service is responding, false otherwise
     */
    public boolean isHealthy() {
        try {
            restTemplate.getForObject("/health", Map.class);
            return true;
        } catch (Exception e) {
            log.warn("Embedding service health check failed: {}", e.getMessage());
            return false;
        }
    }
}