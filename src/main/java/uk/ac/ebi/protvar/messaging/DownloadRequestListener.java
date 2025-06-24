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
import uk.ac.ebi.protvar.processor.DownloadProcessor;
import uk.ac.ebi.protvar.model.DownloadRequest;

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
    private final TaskExecutor downloadJobExecutor;
    private final DownloadProcessor downloadProcessor;

    /**
     * RabbitMQ listener for download requests.
     *
     * - Submits the request to `downloadJobExecutor` for async processing.
     * - Ensures that at most 5 download jobs run concurrently (as controlled by executor and RabbitMQ).
     * - Actual request processing (CSV generation, partitioning, DB access) is delegated to downloadProcessor.
     *
     * This listener is optimised for throughput control: it's more important that jobs finish reliably
     * than that they start immediately.
     */
    @RabbitListener(queues = {RabbitMQConfig.DOWNLOAD_QUEUE}, ackMode = "AUTO")
    //acknowledged right after the method returns — not after the async job finishes
    public void onDownloadRequest(DownloadRequest request, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        // Submit the long job of handling the download request (CSV generation, zipping, etc.)
        downloadJobExecutor.execute(() -> {
            // Processing the request in multiple partitions if necessary
            downloadProcessor.process(request);
        });
    }
    /*
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
                downloadProcessor.process(request);
            });

        } catch (IOException e) {
            LOGGER.error("Failed to ack message for {}: {}", request.getFname(), e.getMessage(), e);
            // Optional: consider dead-lettering this message or alerting ops
        }
    }*/
}
