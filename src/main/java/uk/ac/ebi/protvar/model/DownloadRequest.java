package uk.ac.ebi.protvar.model;

import lombok.Getter;
import lombok.Setter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DownloadRequest input types
 * 1. Text
 * 2. File
 * 3. Id (input id)
 */
@Getter
@Setter
public class DownloadRequest {
    String id;
    LocalDateTime timestamp;
    Path file;
    List<String> inputs;
    boolean function;
    boolean population;
    boolean structure;
    String assembly;
    String email;
    String jobName;
    String url;

    // new id input fields
    String inputId;
    Integer page;
    Integer pageSize;

    public DownloadRequest() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    static DownloadRequest newDownloadRequest(boolean function, boolean population, boolean structure,
                                              String assembly, String email, String jobName) {
        DownloadRequest downloadRequest = new DownloadRequest();
        downloadRequest.setFunction(function);
        downloadRequest.setPopulation(population);
        downloadRequest.setStructure(structure);
        downloadRequest.setAssembly(assembly);
        downloadRequest.setEmail(email);
        downloadRequest.setJobName(jobName);
        return downloadRequest;
    }

    public static DownloadRequest textDownloadRequest(String url, List<String> inputs,
                                                      boolean function, boolean population, boolean structure,
                                                      String assembly, String email, String jobName) {
        DownloadRequest downloadRequest = newDownloadRequest(function, population, structure,
                assembly, email, jobName);
        downloadRequest.setInputs(inputs);
        downloadRequest.setUrl(url.replace("textInput", downloadRequest.getId()));
        return downloadRequest;
    }

    public static DownloadRequest fileDownloadRequest(String url, Path file,
                                                      boolean function, boolean population, boolean structure,
                                                      String assembly, String email, String jobName) {
        DownloadRequest downloadRequest = newDownloadRequest(function, population, structure,
                assembly, email, jobName);
        downloadRequest.setFile(file);
        downloadRequest.setUrl(url.replace("fileInput", downloadRequest.getId()));
        return downloadRequest;
    }

    public static DownloadRequest idDownloadRequest(String url, String inputId, Integer page, Integer pageSize,
                                                      boolean function, boolean population, boolean structure,
                                                      String assembly, String email, String jobName) {
        DownloadRequest downloadRequest = newDownloadRequest(function, population, structure,
                assembly, email, jobName);
        // override download id with input id?
        String id = inputId;    // download request id = inputId[-PAGE][-PAGE_SIZE][-ASSEMBLY], also download filename
        if (page != null) // && != 1?
            id += "-" + page;
        if (pageSize != null) // && != PAGE_SIZE?
            id += "-" + pageSize;
        if (assembly != null) // && assembly != "AUTO"?
            id += "-" + assembly;
        downloadRequest.setId(id);
        downloadRequest.setInputId(inputId); // unchanged, original input ID
        downloadRequest.setPage(page);
        downloadRequest.setPageSize(pageSize);
        downloadRequest.setUrl(url.replace("idInput", downloadRequest.getId()));
        return downloadRequest;
    }
}
