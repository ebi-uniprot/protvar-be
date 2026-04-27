package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Submit response for POST /download. Carries job identity (id, jobName,
 * fileUrl) plus the initial lifecycle status. Lifecycle is the same shape
 * returned by POST /download/status — see {@link DownloadStatus}.
 */
@Getter
@Setter
public class DownloadResponse {
    private String id;          // server-allocated UUID; also the file name stem
    private String jobName;
    private String fileUrl;     // full URL to the generated file
    private DownloadStatus status;
}
