package uk.ac.ebi.protvar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import uk.ac.ebi.protvar.cache.RestTemplateCache;
import uk.ac.ebi.uniprot.domain.entry.comment.Comment;
import uk.ac.ebi.uniprot.mapper.CommentDeserializer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

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
    @ConfigurationProperties("protvar.datasource")
    public DataSource dataSource(){
        return DataSourceBuilder.create().build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @Qualifier("variationRestTemplate")
    //@RequestScope
    public RestTemplate variationRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();// new RestTemplateCache();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(variationURL));
        return restTemplate;
    }

    @Bean
    @Qualifier("proteinsRestTemplate")
    //@RequestScope
    /*
    public RestTemplate proteinsRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();//new RestTemplateCache();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(proteinsURL));
        return restTemplate;
    }*/
    public RestTemplate restTemplate() {
        // Create a custom ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Register custom deserializer
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Comment.class, new CommentDeserializer());
        objectMapper.registerModule(module);

        // Register the ObjectMapper with a custom message converter
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        // Create a RestTemplate with the custom converter
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(proteinsURL));
        restTemplate.setMessageConverters(Collections.singletonList(converter));

        return restTemplate;
    }

    @Bean
    @Qualifier("pdbeRestTemplate")
    //@RequestScope
    public RestTemplate pdbeRestTemplate() {
        RestTemplate restTemplate = new RestTemplateCache();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(pdbeURL));
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

}
