package uk.ac.ebi.protvar;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components())
                .externalDocs(new ExternalDocumentation()
                        .description("Contact us").url("mailto:protvar@ebi.ac.uk")
                )
                .info(new Info()
                        .title("ProtVar API")
                        .contact(new Contact().name("ProtVar").url("https://www.ebi.ac.uk/ProtVar/")));
    }
}
