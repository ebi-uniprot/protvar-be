package uk.ac.ebi.protvar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import uk.ac.ebi.pdbe.api.PDBeAPI;
import uk.ac.ebi.pdbe.api.PDBeAPIImpl;
import uk.ac.ebi.protvar.cache.RestTemplateCache;

import java.nio.charset.StandardCharsets;

@Configuration
public class RestTemplateConfig {
    @Value(("${variation.api}"))
    private String variationAPI;

    @Value(("${protein.api}"))
    private String proteinAPI;

    @Value(("${coordinate.api}"))
    private String coordinateAPI;

    @Value(("${pdbe.api}"))
    private String pdbeAPI;


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
    public PDBeAPI pdBeAPI() {
        PDBeAPI pdBeAPI = new PDBeAPIImpl(pdbeRestTemplate());
        return pdBeAPI;
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
