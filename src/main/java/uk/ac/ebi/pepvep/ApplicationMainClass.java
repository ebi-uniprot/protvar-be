package uk.ac.ebi.pepvep;

import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.util.DefaultUriBuilderFactory;

import io.swagger.annotations.Api;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import uk.ac.ebi.pepvep.cache.RestTemplateCache;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableSwagger2
@Api
@CrossOrigin
public class ApplicationMainClass extends SpringBootServletInitializer {
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
	public Docket swaggerConfigurarion() {
		return new Docket(DocumentationType.SWAGGER_2).select().paths(PathSelectors.ant("/**/pepvep/variant/**"))
				.apis(RequestHandlerSelectors.basePackage("uk.ac.ebi.pepvep")).build();
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

	@Bean(name = "dataSource")
	@ConfigurationProperties("spring.datasource")
	public DataSource dataSource() {
		return DataSourceBuilder.create().build();
	}

}
