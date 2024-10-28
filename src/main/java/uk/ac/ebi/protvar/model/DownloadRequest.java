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
    InputType type = InputType.ID; // default
    String input;
    /*  type                 input
     *  ----                 -----
     *  ID                   input id - checksum of original input
     *  PROTEIN_ACCESSION    protein accession
     *  SINGLE_VARIANT       single variant
     */
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
