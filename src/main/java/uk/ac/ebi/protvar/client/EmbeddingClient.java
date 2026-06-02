package uk.ac.ebi.protvar.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.protvar.config.ModelRegistryProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class EmbeddingClient {

    private final RestTemplate restTemplate;
    private final ModelRegistryProperties modelRegistry;

    public EmbeddingClient(@Qualifier("embeddingRestTemplate") RestTemplate restTemplate,
                           ModelRegistryProperties modelRegistry) {
        this.restTemplate = restTemplate;
        this.modelRegistry = modelRegistry;
    }

    public Optional<List<Number>> getEmbedding(String queryText, String model) {
        String serviceUrl = modelRegistry.resolveServiceUrl(model);
        try {
            Map<String, String> requestBody = Map.of("text", queryText);

            @SuppressWarnings("unchecked")
            Map<String, List<Number>> response = restTemplate.postForObject(
                    serviceUrl + "/embed",
                    requestBody,
                    Map.class
            );

            if (response == null || response.get("embedding") == null) {
                log.warn("Embedding service returned null response for text: {}", queryText);
                return Optional.empty();
            }

            return Optional.of(response.get("embedding"));

        } catch (RestClientException e) {
            log.error("Failed to get embedding from service for text: {}. Error: {}", queryText, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error getting embedding for text: {}", queryText, e);
            return Optional.empty();
        }
    }

    public boolean isHealthy(String model) {
        String serviceUrl = modelRegistry.resolveServiceUrl(model);
        try {
            restTemplate.getForObject(serviceUrl + "/health", Map.class);
            return true;
        } catch (Exception e) {
            log.warn("Embedding service health check failed for model {}: {}", model, e.getMessage());
            return false;
        }
    }

    public boolean isHealthy() {
        return isHealthy(modelRegistry.getDefaultModel());
    }
}
