package uk.ac.ebi.protvar;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
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
@EnableScheduling
@OpenAPIDefinition(info =
	@Info(
		title = "ProtVar API",
		version = "2.1",
		description = "Programmatic access to ProtVar: map human missense variants to proteins and retrieve annotations. "
				+ "Typical flow: POST /input/text → GET /mapping/{inputId} → POST /download.",
		license = @License(name = "Creative Commons", url = "https://creativecommons.org/licenses/by/4.0/"),
		contact = @Contact(url = "https://www.ebi.ac.uk/ProtVar", name = "ProtVar", email = "protvar@ebi.ac.uk")
	),
	tags = {
		@Tag(name = "Input", description = "Submit variants; returns an input ID."),
		@Tag(name = "Mapping", description = "Map variants to proteins; browse and filter by identifier."),
		@Tag(name = "Download", description = "Queue and fetch CSV downloads."),
		@Tag(name = "Annotation", description = "Functional, population and structural annotations for a protein position."),
		@Tag(name = "Score", description = "Amino-acid-level scores."),
		@Tag(name = "Prediction", description = "Structure-based predictions."),
		@Tag(name = "Allele frequency", description = "gnomAD allele frequencies."),
		@Tag(name = "Assembly", description = "GRCh37 ↔ GRCh38 coordinate conversion."),
		@Tag(name = "Semantic search", description = "Free-text search over protein function and population text."),
		@Tag(name = "Stats", description = "Release statistics."),
		@Tag(name = "Status", description = "Service health.")
	}
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
