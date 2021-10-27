package uk.ac.ebi.pepvep;

import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.util.DefaultUriBuilderFactory;

import uk.ac.ebi.pepvep.cache.RestTemplateCache;

@SpringBootApplication
@CrossOrigin
public class ApplicationMainClass {
	@Value(("${variation.api}"))
	private String variationAPI;

	@Value(("${protein.api}"))
	private String proteinAPI;

	@Value(("${coordinate.api}"))
	private String coordinateAPI;

	@Value(("${pdbe.api}"))
	private String pdbeAPI;

	public static void main(String[] args) {
		SpringApplication.run(ApplicationMainClass.class, args);
	}

	@Bean
	public CorsFilter corsFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin("*");
		config.addAllowedHeader("*");
		config.addAllowedMethod("OPTIONS");
		config.addAllowedMethod("HEAD");
		config.addAllowedMethod("GET");
		config.addAllowedMethod("PUT");
		config.addAllowedMethod("POST");
		config.addAllowedMethod("DELETE");
		config.addAllowedMethod("PATCH");
		source.registerCorsConfiguration("/**", config);
		return new CorsFilter(source);
	}

	@Bean
	@RequestScope
	public RestTemplate variantRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(variationAPI));
		return restTemplate;
	}

	@Bean
	@RequestScope
	public RestTemplate proteinRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(proteinAPI));
		return restTemplate;
	}

	@Bean
	@RequestScope
	public RestTemplate coordinateRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(coordinateAPI));
		return restTemplate;
	}

	@Bean
	@RequestScope
	public RestTemplate pdbeRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(pdbeAPI));
		return restTemplate;
	}

	@Bean
	public OpenAPI customOpenAPI() {
		var description = "Welcome to api documentation. You can know how to use api and try it with examples provided." +
			" REST api uses OpenAPI 3. Meaning you can use utils like openapi-generator to generate model classes on run.";
		return new OpenAPI()
			.components(new Components())
			.externalDocs(new ExternalDocumentation()
				.description("openapi-generator").url("https://github.com/OpenAPITools/openapi-generator")
			)
			.info(new Info().title("Pepvep API docs & try").description(description)
					.contact(new Contact().name("PepVep").email("abc").url("/sdfs/sdf/sd"))
			);
	}
}
