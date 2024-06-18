package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DownloadResponse {
    //String inputType;
    LocalDateTime requested;
    String downloadId;
    int status;
    String jobName;
    String url;
}
