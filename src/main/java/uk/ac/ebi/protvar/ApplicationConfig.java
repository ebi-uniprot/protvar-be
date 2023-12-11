package uk.ac.ebi.protvar;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import uk.ac.ebi.pdbe.api.PDBeAPI;
import uk.ac.ebi.pdbe.api.PDBeAPIImpl;
import uk.ac.ebi.protvar.cache.RestTemplateCache;
import uk.ac.ebi.uniprot.coordinates.api.CoordinatesAPI;
import uk.ac.ebi.uniprot.coordinates.api.CoordinatesAPIImpl;
import uk.ac.ebi.uniprot.proteins.api.ProteinsAPI;
import uk.ac.ebi.uniprot.proteins.api.ProteinsAPIImpl;
import uk.ac.ebi.uniprot.variation.api.VariationAPI;
import uk.ac.ebi.uniprot.variation.api.VariationAPIImpl;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@Configuration
public class ApplicationConfig {

    @Value(("${uniprot.proteins.api.url}"))
    private String proteinsURL;

    @Value(("${uniprot.variation.api.url}"))
    private String variationURL;

    @Value(("${uniprot.coordinates.api.url}"))
    private String coordinatesURL;

    @Value(("${pdbe.best-structures.api.url}"))
    private String pdbeURL;

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource")
    public DataSource dataSource(){
        return DataSourceBuilder.create().build();
    }
/*
    @Bean
    @ConfigurationProperties("ensembl.datasource")
    public DataSource ensemblDataSource(){
        return DataSourceBuilder.create().build();
    }
*/
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
/*
    @Bean
    public JdbcTemplate ensemblJdbcTemplate(@Qualifier("ensemblDataSource") DataSource ensemblDataSource) {
        return new JdbcTemplate(ensemblDataSource);
    }
*/
    @Bean
    //@RequestScope
    public RestTemplate variantRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();// new RestTemplateCache();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(variationURL));
        return restTemplate;
    }

    @Bean
    //@RequestScope
    public RestTemplate proteinRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();//new RestTemplateCache();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(proteinsURL));
        return restTemplate;
    }

    @Bean
    //@RequestScope
    public RestTemplate coordinateRestTemplate() {
        RestTemplate restTemplate = new RestTemplateCache();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(coordinatesURL));
        return restTemplate;
    }

    @Bean
    //@RequestScope
    public RestTemplate pdbeRestTemplate() {
        RestTemplate restTemplate = new RestTemplateCache();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(pdbeURL));
        return restTemplate;
    }

    @Bean
    public PDBeAPI pdbeAPI() {
        PDBeAPI pdbeAPI = new PDBeAPIImpl(pdbeRestTemplate());
        return pdbeAPI;
    }

    @Bean
    public VariationAPI variationAPI() {
        return new VariationAPIImpl(variantRestTemplate());
    }
    @Bean
    public ProteinsAPI proteinsAPI() {
        return new ProteinsAPIImpl(proteinRestTemplate());
    }
    @Bean
    public CoordinatesAPI coordinatesAPI() {
        return new CoordinatesAPIImpl(coordinateRestTemplate());
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

}
