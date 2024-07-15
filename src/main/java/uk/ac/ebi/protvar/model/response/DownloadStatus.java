package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class DownloadStatus {
    Integer status;
    Long size;
    public DownloadStatus(Integer status) {
        this.status = status;
    }
}