package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DownloadJobStatus {
    PENDING,     // job queued, file not yet on disk
    PROCESSING,  // CSV generated, ZIP not ready yet
    READY;       // ZIP available for download

    @JsonValue
    public String value() {
        return this.name().toLowerCase();
    }
}
