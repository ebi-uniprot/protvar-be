package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.response.Download;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class DownloadService {
    private String downloadDir;

    public Download newDownload(String type) {
        Download download = new Download(type);
        download.setRequested(LocalDateTime.now());
        download.setDownloadId(UUID.randomUUID().toString());
        download.setStatus(-1);
        return download;
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
