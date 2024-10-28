package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class DownloadStatus {
    int status;
    long size;
    public DownloadStatus(int status) {
        this.status = status;
    }
}