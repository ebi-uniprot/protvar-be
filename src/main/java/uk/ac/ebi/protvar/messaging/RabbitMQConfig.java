package uk.ac.ebi.protvar.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;

@Configuration
public class RabbitMQConfig {

    public final static String DOWNLOAD_QUEUE = "q.download.request";

    @Bean
    public Queue createDownloadRequestQueue() {
        return new Queue(DOWNLOAD_QUEUE);
    }

    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }
}