package uk.ac.ebi.protvar;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.concurrent.Executor;

@SpringBootApplication
//@CrossOrigin
@EnableCaching
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

	@Value("${app.data.folder}")
	private String dataFolder; // app's permanent generated files (main data)

	@Value("${app.tmp.folder}")
	private String tmpFolder; // temp files

	@Value("${logging.file.path}")
	private String logFolder; // logs folder

	@PostConstruct
	public void initFolders() {
		new File(dataFolder).mkdirs(); // this is /data
		new File(tmpFolder).mkdirs();  // this is /data/tmp
		new File(logFolder).mkdirs(); // for /data/logs
	}

	public static void main(String[] args) {
		SpringApplication.run(ApplicationMainClass.class, args);
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
