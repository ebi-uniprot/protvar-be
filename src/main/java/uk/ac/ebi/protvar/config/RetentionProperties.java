package uk.ac.ebi.protvar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.retention")
public class RetentionProperties {

    private Duration submissions = Duration.ofDays(90);
    private Duration downloads = Duration.ofDays(30);
}
