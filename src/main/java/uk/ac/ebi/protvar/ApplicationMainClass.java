package uk.ac.ebi.protvar;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import uk.ac.ebi.pdbe.cache.PDBeCache;

import java.util.concurrent.Executor;

@SpringBootApplication
@CrossOrigin
@EnableAsync
public class ApplicationMainClass {

	@Value(("${protvar.data}"))
	private String protVarData;

	public static void main(String[] args) {
		SpringApplication.run(ApplicationMainClass.class, args);
	}

	@Bean
	public String downloadDir() {
		return protVarData;
	}

	@Bean
	public PDBeCache pdBeCache() {
		PDBeCache pdBeCache = new PDBeCache(downloadDir());
		//pdBeCache.initialize();
		return pdBeCache;
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
