package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.messaging.RabbitMQConfig;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.response.DownloadResponse;
import uk.ac.ebi.protvar.model.response.DownloadStatus;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DownloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadService.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.data.folder}")
    private String dataFolder;

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

    public FileInputStream getFileResource(String filename) {
        Path filePath = Path.of(dataFolder, filename + ".csv.zip");
        try {
            return new FileInputStream(filePath.toFile());
        }
        catch (FileNotFoundException ex) {
            return null;
        }
    }

    public Map<String, DownloadStatus> getDownloadStatus(List<String> filenames) {
        Map<String, DownloadStatus> resultMap = new LinkedHashMap<>();
        filenames.stream().forEach(filename -> {
            Path csvFilePath = Path.of(dataFolder, filename + ".csv");
            Path zipFilePath = csvFilePath.resolveSibling(csvFilePath.getFileName() + ".zip");
            if (Files.exists(zipFilePath)) {
                long size = 0;
                try {
                    size = Files.size(zipFilePath);
                } catch (IOException e) {
                    LOGGER.error("Error getting file size for: {}", zipFilePath, e);
                }
                resultMap.put(filename, new DownloadStatus(1, size));
            }
            else if (Files.exists(csvFilePath))
                resultMap.put(filename, new DownloadStatus(0));
            else
                resultMap.put(filename, new DownloadStatus(-1));
        });
        return resultMap;
    }
}
