package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
        LOGGER.info("Queuing request " + downloadRequest.getFname());
        rabbitTemplate.convertAndSend("", RabbitMQConfig.DOWNLOAD_QUEUE, downloadRequest);

        DownloadResponse response = new DownloadResponse();
        //response.setInputType(downloadRequest.getFile() == null ? TEXT_INPUT : FILE_INPUT);
        response.setRequested(downloadRequest.getTimestamp());
        response.setDownloadId(downloadRequest.getFname());
        response.setStatus(-1);
        response.setJobName(downloadRequest.getJobName());
        response.setUrl(downloadRequest.getUrl());
        return response;
    }

    /**
     * ackMode="NONE" here means that the listener acknowledges
     * immediately on receiving a message (auto ack in rabbitmq).
     * We choose this mode to ensure when jobs fail, unack messages
     * (which would be the default mode) do not remain in the queue,
     * and avoid the infinite cycle of trying to process them - likely
     * to fail - on app restart etc.
     * If an error or exception happens during job processing, these are
     * handled e.g. failed job email sent to the user and dev.
     * TO CHECK!
     * @param request
     * @throws InterruptedException
     */

    @RabbitListener(queues = {RabbitMQConfig.DOWNLOAD_QUEUE}, concurrency="2", ackMode = "NONE")
    public void onDownloadRequest(DownloadRequest request) {
        LOGGER.info("Processing request " + request.getFname());
        csvDataFetcher.writeCSVResult(request);
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


}
