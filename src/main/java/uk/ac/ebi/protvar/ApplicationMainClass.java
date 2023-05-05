package uk.ac.ebi.protvar;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import uk.ac.ebi.protvar.cache.RestTemplateCache;

@SpringBootApplication
@CrossOrigin
@EnableAsync
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
	public RequestContextListener requestContextListener() {
		return new RequestContextListener();
	}

	@Bean
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("Async-");
		executor.initialize();
		return executor;
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
	//@RequestScope
	public RestTemplate variantRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(variationAPI));
		return restTemplate;
	}

	@Bean
	//@RequestScope
	public RestTemplate proteinRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(proteinAPI));
		return restTemplate;
	}

	@Bean
	//@RequestScope
	public RestTemplate coordinateRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(coordinateAPI));
		return restTemplate;
	}

	@Bean
	//@RequestScope
	public RestTemplate pdbeRestTemplate() {
		RestTemplate restTemplate = new RestTemplateCache();
		restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(pdbeAPI));
		return restTemplate;
	}

	@Bean
	RestTemplateCustomizer retryRestTemplateCustomizer() {
		return restTemplate -> restTemplate.getInterceptors().add((request, body, execution) -> {

			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));
			try {
				return retryTemplate.execute(context -> {
					System.out.println("start retrying ....");
					return execution.execute(request, body);
				});
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		});
	}

	@Bean
	public OpenAPI customOpenAPI() {
		var description = "ProtVar REST API is a programmatic way to obtain information from ProtVar.\n" +
			"You can query:\n" +
			"1) A list of variants via their genomic coordinates and choose which annotations you require. These can be posted as a list and then downloaded or emailed or a file can be uploaded.\n" +
			"2) Genomic coordinates to retrieve mappings to positions in proteins for all isoforms\n" +
			"3) Individual amino acid positions to retrieve functional/structural/co-located variant annotations via a json object\n\n\n" +
			"REST API uses OpenAPI 3 which means you can use utils like " +
			"<a href='https://github.com/OpenAPITools/openapi-generator' target='_new'>openapi-generator</a> to generate model classes\n\n"+
			"You can look at the examples below to see how it works. \n";
		return new OpenAPI()
			.components(new Components())
			.externalDocs(new ExternalDocumentation()
				.description("Contact us").url("https://www.ebi.ac.uk/ProtVar/contact")
			)
			.info(new Info().title("ProtVar REST API").description(description)
					.contact(new Contact().name("ProtVar").url("https://www.ebi.ac.uk/ProtVar/"))
			);
	}
}
