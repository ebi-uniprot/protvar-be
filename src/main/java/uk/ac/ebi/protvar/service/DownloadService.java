package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.messaging.RabbitMQConfig;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.response.DownloadState;
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
    private final DownloadStatusService downloadStatusService;

    @Value("${app.data.folder}")
    private String dataFolder;

    public DownloadResponse queueRequest(DownloadRequest downloadRequest) {
        String id = downloadRequest.getFname();

        try {
            rabbitTemplate.convertAndSend("", RabbitMQConfig.DOWNLOAD_QUEUE, downloadRequest);
            downloadStatusService.markQueued(id);
            LOGGER.info("Queued request: {}", id);
        } catch (Exception e) {
            LOGGER.error("Error queuing request {}", id, e);
            downloadStatusService.markFailed(id, DownloadStatusService.MSG_QUEUE_FAILED);
        }

        DownloadResponse response = new DownloadResponse();
        response.setId(id);
        response.setJobName(downloadRequest.getJobName());
        response.setFileUrl(downloadRequest.getUrl());
        response.setStatus(downloadStatusService.get(id));
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

    /**
     * Returns the lifecycle status for each id. Reads from Redis as the primary
     * source; falls back to filesystem inspection only when Redis has no entry
     * (TTL expired but file may still exist).
     */
    public Map<String, DownloadStatus> getDownloadStatus(List<String> ids) {
        Map<String, DownloadStatus> resultMap = new LinkedHashMap<>();
        for (String id : ids) {
            DownloadStatus status = downloadStatusService.get(id);
            if (status == null) {
                status = filesystemFallback(id);
            }
            resultMap.put(id, status);
        }
        return resultMap;
    }

    private DownloadStatus filesystemFallback(String id) {
        Path csvFilePath = Path.of(dataFolder, id + ".csv");
        Path zipFilePath = csvFilePath.resolveSibling(csvFilePath.getFileName() + ".zip");

        if (Files.exists(zipFilePath)) {
            long size = 0;
            try {
                size = Files.size(zipFilePath);
            } catch (IOException e) {
                LOGGER.error("Error getting file size for: {}", zipFilePath, e);
            }
            return DownloadStatus.builder()
                    .state(DownloadState.READY)
                    .size(size)
                    .build();
        }
        if (Files.exists(csvFilePath)) {
            return DownloadStatus.builder().state(DownloadState.PROCESSING).build();
        }
        // No Redis entry, no files — id is unknown to the BE.
        return DownloadStatus.builder().state(DownloadState.EXPIRED).build();
    }
}
