package uk.ac.ebi.protvar.messaging;

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
}