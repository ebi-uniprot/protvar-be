package uk.ac.ebi.protvar;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
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
@OpenAPIDefinition(info =
	@Info(
		title = "ProtVar API",
		version = "1.4",
		description = "ProtVar API's Swagger documentation page. The API serves the ProtVar UI and enables programmatic access to the ProtVar data.",
		license = @License(name = "Creative Commons", url = "https://creativecommons.org/licenses/by/4.0/"),
		contact = @Contact(url = "https://www.ebi.ac.uk/ProtVar", name = "ProtVar", email = "protvar@ebi.ac.uk")
	)
)
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

	// TODO: use redis?
	@Bean
	public PDBeCache pdbeCache() {
		PDBeCache pdbeCache = new PDBeCache(downloadDir());
		//pdBeCache.initialize();
		return pdbeCache;
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

}
