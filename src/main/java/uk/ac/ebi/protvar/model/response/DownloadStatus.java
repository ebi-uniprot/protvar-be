package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class DownloadStatus {
    DownloadJobStatus status;
    long size;

    public DownloadStatus(DownloadJobStatus status) {
        this.status = status;
    }
}
