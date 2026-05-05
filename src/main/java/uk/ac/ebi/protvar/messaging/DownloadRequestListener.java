package uk.ac.ebi.protvar.messaging;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.processor.DownloadProcessor;
import uk.ac.ebi.protvar.model.DownloadRequest;

import java.io.IOException;

/**
 * Consumes download requests from RabbitMQ and runs them inline on the
 * listener thread. Concurrency=5 + prefetch=1 caps in-flight jobs at 5.
 *
 * Manual ack is critical: {@link DownloadProcessor#process} catches its own
 * exceptions and records the outcome in Redis, so on a normal return (success
 * or recorded failure) we ack and the message is gone. The case we deliberately
 * do NOT ack is JVM termination (SIGKILL / OOM / crash) — the unacked message
 * stays on the queue and Rabbit redelivers it when a consumer reconnects.
 * Without this, a SIGKILLed worker leaves the user's job stuck on "processing"
 * forever (Redis state was set, but markFailed never ran).
 */
@Component
@RequiredArgsConstructor
public class DownloadRequestListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadRequestListener.class);
    private final DownloadProcessor downloadProcessor;

    @RabbitListener(queues = {RabbitMQConfig.DOWNLOAD_QUEUE}, ackMode = "MANUAL", concurrency = "5")
    public void onDownloadRequest(DownloadRequest request, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            downloadProcessor.process(request);
        } finally {
            try {
                channel.basicAck(tag, false);
            } catch (IOException e) {
                LOGGER.warn("Failed to ack download {}: {}", request.getFname(), e.getMessage());
            }
        }
    }
}
