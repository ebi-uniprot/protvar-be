package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DownloadResponse {
    String id;           // download file name stem (no extension)
    String jobName;
    String fileUrl;      // full URL to the generated file
    DownloadJobStatus status;
    LocalDateTime requestedAt;
}
