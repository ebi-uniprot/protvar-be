package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DownloadResponse {
    //String inputType;
    LocalDateTime requested; // "requested": "2024-06-20T22:02:31.157133154" in json
    String downloadId; // corresponds to the input ID
    int status;
    String jobName;
    String url;
}
