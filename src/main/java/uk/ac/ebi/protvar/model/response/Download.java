package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Download {
    String inputType;
    LocalDateTime requested;
    String downloadId;
    int status;
    String url;
    String jobName;

    public Download(String type) {
        this.inputType = type;
    }
}
