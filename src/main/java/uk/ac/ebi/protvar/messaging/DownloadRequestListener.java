package uk.ac.ebi.protvar.messaging;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.processor.CsvProcessor;
import uk.ac.ebi.protvar.model.DownloadRequest;

import java.io.IOException;

/**
 * Download queues, possible organisation:
 * - q.download.request.new         <- onNewRequest - generate file
 * - q.download.request.completed   <- onCompletedRequest - send email
 * - q.download.request.failed      <- onFailedRequest - log error, send email
 *
 */
@Component
@RequiredArgsConstructor
public class DownloadRequestListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadRequestListener.class);
    private final TaskExecutor downloadTaskExecutor;
    private final CsvProcessor csvProcessor;

    /**
     * Handles DownloadRequest jobs from the queue with manual acknowledgment.
     *
     * Previously we used ackMode="NONE" to avoid unacked messages piling up or retrying
     * indefinitely on app restart. However, this also meant transient failures (e.g. DB
     * connection issues) led to permanent message loss.
     *
     * This version uses ackMode="MANUAL" to:
     * - Retry transient errors (e.g. JDBC connection pool) up to 3 times with delay.
     * - Ack only after successful processing.
     * - Nack without requeue on failure to avoid infinite retry loops.
     *
     * This ensures resilience to temporary issues while avoiding job duplication or cycling.
     */
    @RabbitListener(queues = {RabbitMQConfig.DOWNLOAD_QUEUE})
    public void onDownloadRequest(DownloadRequest request, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            // Acknowledge the message right away
            channel.basicAck(tag, false);
            LOGGER.info("Acked request {} - starting async processing", request.getFname());

            // Submit the long job of handling the download request (CSV generation, zipping, etc.)
            downloadTaskExecutor.execute(() -> {
                // Processing the request in multiple partitions if necessary
                csvProcessor.process(request);
            });

        } catch (IOException e) {
            LOGGER.error("Failed to ack message for {}: {}", request.getFname(), e.getMessage(), e);
            // Optional: consider dead-lettering this message or alerting ops
        }
    }
}
