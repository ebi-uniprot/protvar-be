package uk.ac.ebi.protvar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DownloadService {

    @Value(("${protvar.data}"))
    private String downloadDir;

    public FileInputStream getFileResource(String id) {
        String fileName = downloadDir + "/" + id + ".csv";
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
            if (Files.exists(Paths.get(fileName)))
                resultMap.put(id, 1);
            else if (Files.exists(Paths.get(fileName + ".tmp")))
                resultMap.put(id, 0);
            else
                resultMap.put(id, -1);
        });
        return resultMap;
    }


}
