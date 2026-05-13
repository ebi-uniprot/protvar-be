package uk.ac.ebi.protvar.resolver;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({ "test" })
@SpringBootApplication
public class AppTestConfig {
}