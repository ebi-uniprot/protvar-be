package uk.ac.ebi.protvar.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class DownloadRequest {
    LocalDateTime timestamp;
    String id;
    ResultType type = ResultType.CUSTOM_INPUT; // default
    boolean function;
    boolean population;
    boolean structure;
    Integer page;
    Integer pageSize;
    String assembly;
    String email;
    String jobName;

    String fname; // filename: <id>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY]
    String url;
}
