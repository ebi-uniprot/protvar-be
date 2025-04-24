package uk.ac.ebi.protvar.service;

import com.rabbitmq.client.Channel;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.fetcher.csv.CSVDataFetcher;
import uk.ac.ebi.protvar.messaging.RabbitMQConfig;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.response.DownloadResponse;
import uk.ac.ebi.protvar.model.response.DownloadStatus;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Download queues
 * - q.download.request.new         <- onNewRequest - generate file
 * - q.download.request.completed   <- onCompletedRequest - send email
 * - q.download.request.failed      <- onFailedRequest - log error, send email
 *
 */
@Service
@AllArgsConstructor
public class DownloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadService.class);
    private String downloadDir;
    private CSVDataFetcher csvDataFetcher;
    private final RabbitTemplate rabbitTemplate;

    public Path tmpPath() {
        return Path.of(downloadDir, "tmp");
    }

    public DownloadResponse queueRequest(DownloadRequest downloadRequest) {
        LOGGER.info("Queuing request: {}", downloadRequest.getFname());
        try {
            rabbitTemplate.convertAndSend("", RabbitMQConfig.DOWNLOAD_QUEUE, downloadRequest);
            LOGGER.info("Successfully queued request: {}", downloadRequest.getFname());
        } catch (Exception e) {
            LOGGER.error("Error queuing request", e);
        }

        DownloadResponse response = new DownloadResponse();
        //response.setInputType(downloadRequest.getFile() == null ? TEXT_INPUT : FILE_INPUT);
        response.setRequested(downloadRequest.getTimestamp());
        response.setDownloadId(downloadRequest.getFname());
        response.setStatus(-1);
        response.setJobName(downloadRequest.getJobName());
        response.setUrl(downloadRequest.getUrl());
        return response;
    }

    private static final int MAX_RETRIES = 3;

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
    @RabbitListener(
            queues = {RabbitMQConfig.DOWNLOAD_QUEUE},
            concurrency = "1-3",
            ackMode = "MANUAL"
    )
    public void onDownloadRequest(DownloadRequest request,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        int attempt = 0;
        boolean success = false;

        while (attempt < MAX_RETRIES && !success) {
            attempt++;
            try {
                LOGGER.info("Attempt {} to process request {}", attempt, request.getFname());
                csvDataFetcher.writeCSVResult(request);
                channel.basicAck(tag, false); // Ack only on success
                success = true;
            } catch (Exception e) {
                if (isConnectionIssue(e)) {
                    LOGGER.warn("DB connection issue on attempt {}: {}", attempt, e.getMessage());
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 5000)); // 2 to 5 sec delay
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    LOGGER.error("Non-retryable failure. Rejecting request {}", request.getFname(), e);
                    nackAndExit(channel, tag, false); // don't requeue
                    return;
                }
            }
        }
        if (!success) {
            LOGGER.error("Max retries reached. Rejecting request {}", request.getFname());
            nackAndExit(channel, tag, false); // No requeue to avoid retry loop
        }
    }

    public FileInputStream getFileResource(String filename) {
        String fileName = downloadDir + "/" + filename + ".csv.zip";
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(fileName);
        }
        catch (FileNotFoundException ex) {
            fileInputStream = null;
        }
        return fileInputStream;
    }

    public Map<String, DownloadStatus> getDownloadStatus(List<String> fs) {
        Map<String, DownloadStatus> resultMap = new LinkedHashMap<>();
        fs.stream().forEach(filename -> {
            String csvFile = downloadDir + "/" + filename + ".csv";
            String zipFile = csvFile + ".zip";
            Path zipFilePath = Paths.get(zipFile);
            if (Files.exists(zipFilePath)) {
                long bytes = 0;
                try {
                    bytes = Files.size(zipFilePath);
                } catch (IOException e) {
                    LOGGER.error("Error getting file size for: " + zipFile);
                }
                resultMap.put(filename, new DownloadStatus(1, bytes));
            }
            else if (Files.exists(Paths.get(csvFile)))
                resultMap.put(filename, new DownloadStatus(0));
            else
                resultMap.put(filename, new DownloadStatus(-1));
        });
        return resultMap;
    }

    // Helper: Detect DB Connection Errors
    private boolean isConnectionIssue(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.sql.SQLTransientConnectionException
                    || cause instanceof org.springframework.jdbc.CannotGetJdbcConnectionException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    // Helper: Nack and Exit
    private void nackAndExit(Channel channel, long tag, boolean requeue) {
        try {
            channel.basicNack(tag, false, requeue);
        } catch (IOException e) {
            LOGGER.error("Failed to nack message: ", e);
        }
    }

}
