package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.client.EmbeddingClient;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;

    public Optional<List<Number>> generateEmbedding(String text, String model) {
        if (text == null || text.isBlank()) {
            log.warn("Attempted to generate embedding for empty text");
            return Optional.empty();
        }
        return embeddingClient.getEmbedding(text, model);
    }

    public boolean isServiceAvailable() {
        return embeddingClient.isHealthy();
    }
}
