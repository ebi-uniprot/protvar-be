package uk.ac.ebi.protvar.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class DownloadRequest {
    UUID id;
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
}
