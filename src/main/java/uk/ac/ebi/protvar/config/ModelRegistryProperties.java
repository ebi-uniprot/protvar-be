package uk.ac.ebi.protvar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class ModelRegistryProperties {

    private String defaultModel = "mpnet";
    private Map<String, ModelConfig> models = new LinkedHashMap<>();

    @Data
    public static class ModelConfig {
        private String label;
        private String description;
        private String hfName;
        private int dim;
        private boolean enabled;
        private String serviceUrl;
    }

    public String resolveServiceUrl(String modelId) {
        ModelConfig config = models.get(modelId);
        if (config != null && config.getServiceUrl() != null) {
            return config.getServiceUrl();
        }
        return "http://embedding-service-" + modelId + ":8000";
    }
}
