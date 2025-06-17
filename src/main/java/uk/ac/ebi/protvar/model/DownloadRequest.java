package uk.ac.ebi.protvar.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import uk.ac.ebi.protvar.types.IdentifierType;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class DownloadRequest {
    LocalDateTime timestamp;
    IdentifierType type; // null value means single variant input
    String input;
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
