package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DownloadState {
    QUEUED,      // submitted to the queue, awaiting a worker
    PROCESSING,  // worker has started, CSV being generated
    READY,       // ZIP available for download
    FAILED,      // processing aborted with an error (see DownloadStatus.message)
    EXPIRED;     // no record exists — Redis TTL elapsed and no file on disk

    @JsonValue
    public String value() {
        return this.name().toLowerCase();
    }
}
