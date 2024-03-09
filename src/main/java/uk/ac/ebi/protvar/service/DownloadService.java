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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
    public static final String FILE_INPUT = "FILE";
    public static final String TEXT_INPUT = "TEXT";
    private String downloadDir;
    private CSVDataFetcher csvDataFetcher;
    private final RabbitTemplate rabbitTemplate;

    public Path tmpPath() {
        return Path.of(downloadDir, "tmp");
    }

    public DownloadResponse queueRequest(DownloadRequest downloadRequest) {
        LOGGER.info("Queuing request " + downloadRequest.getId());
        rabbitTemplate.convertAndSend("", RabbitMQConfig.DOWNLOAD_QUEUE, downloadRequest);

        DownloadResponse response = new DownloadResponse();
        response.setInputType(downloadRequest.getFile() == null ? TEXT_INPUT : FILE_INPUT);
        response.setRequested(downloadRequest.getTimestamp());
        response.setDownloadId(downloadRequest.getId().toString());
        response.setStatus(-1);
        response.setJobName(downloadRequest.getJobName());
        response.setUrl(downloadRequest.getUrl());
        return response;
    }

    @RabbitListener(queues = {RabbitMQConfig.DOWNLOAD_QUEUE}, concurrency="2")
    public void onDownloadRequest(DownloadRequest request) {
        LOGGER.info("Processing request " + request.getId());
        csvDataFetcher.writeCSVResult(request);
    }

    public FileInputStream getFileResource(String id) {
        String fileName = downloadDir + "/" + id + ".csv.zip";
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(fileName);
        }
        catch (FileNotFoundException ex) {
            fileInputStream = null;
        }
        return fileInputStream;
    }

    public Map<String, Integer> getDownloadStatus(List<String> ids) {
        Map<String, Integer> resultMap = new LinkedHashMap<>();
        ids.stream().forEach(id -> {
            String fileName = downloadDir + "/" + id + ".csv";
            if (Files.exists(Paths.get(fileName + ".zip")))
                resultMap.put(id, 1);
            else if (Files.exists(Paths.get(fileName)))
                resultMap.put(id, 0);
            else
                resultMap.put(id, -1);
        });
        return resultMap;
    }


}
