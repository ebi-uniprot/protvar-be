package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.response.DownloadResponse;
import uk.ac.ebi.protvar.model.response.DownloadStatsResponse;
import uk.ac.ebi.protvar.model.response.DownloadStatus;
import uk.ac.ebi.protvar.processor.DownloadFileCleanupTask;
import uk.ac.ebi.protvar.repo.CodingVariantRepo;
import uk.ac.ebi.protvar.service.DownloadService;
import uk.ac.ebi.protvar.service.DownloadStatusService;
import uk.ac.ebi.protvar.service.MappingService;
import uk.ac.ebi.protvar.service.UploadFilterService;
import uk.ac.ebi.protvar.utils.MappingRequestValidator;

import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Tag(name = "Download")
@RestController
@RequestMapping("/download") // Base path for all download-related endpoints
@CrossOrigin
@RequiredArgsConstructor
public class DownloadController implements WebMvcConfigurer {

    private final static String SUMMARY = "Queue a CSV download";
    private final static String DESC = "Same request as POST /mapping, plus annotation options. "
            + "Returns a job id; poll /download/status, then GET /download/{id}. full=true downloads every page (max 100,000 rows).";

    /**
     * Hard cap on rows for full downloads. Larger requests are rejected at
     * submit time with {@link DownloadStatusService#MSG_TOO_LARGE}; users are
     * directed to the FTP site for bulk pre-computed datasets.
     */
    public static final long MAX_FULL_DOWNLOAD_ROWS = 100_000L;

    static final String MSG_PREPARING = "This upload is still being prepared for filtering. "
            + "Please try the download again once the filtered results show.";

    private final DownloadService downloadService;
    private final DownloadStatusService downloadStatusService;
    private final DownloadFileCleanupTask cleanupTask;
    private final MappingService mappingService;

    @Operation(summary = SUMMARY, description = DESC)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> downloadGet(@Valid @RequestBody
                                         DownloadRequest request,
                                         HttpServletRequest http) {
        return handleDownload(request, http);
    }

    @Operation(summary = SUMMARY, description = DESC)
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> downloadPost(@Valid @RequestBody @ModelAttribute
                                          DownloadRequest request,
                                          HttpServletRequest http) {
        return handleDownload(request, http);
    }

    /**
     * Handle download request: validate, enforce row cap on full downloads,
     * allocate UUID job ID, queue.
     */
    public ResponseEntity<?> handleDownload(DownloadRequest request, HttpServletRequest http) {
        Optional<String> validationError = MappingRequestValidator.validate(request);
        if (validationError.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError.get()));
        }

        // a filtered upload downloads its passing lines, so it must be prepared for filtering first
        Long filteredTotal = null;
        if (UploadFilterService.applies(request)) {
            filteredTotal = mappingService.countFilteredUpload(request);
            if (filteredTotal == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", MSG_PREPARING));
            }
        }

        if (Boolean.TRUE.equals(request.getFull())) {
            long total = filteredTotal != null ? filteredTotal : mappingService.countInputs(request);
            // total = -1: the count timed out — unknown size, too risky to queue; say so (not "too large").
            // total > MAX or capped to a known-too-many sentinel — reject.
            // Filter-only: browse pages up to COUNT_CAP results (its count stops at COUNT_CAP + 1),
            // so a full download is capped there too.
            if (total < 0) {
                downloadStatusService.increment("rejected");
                return ResponseEntity.badRequest().body(Map.of("error", DownloadStatusService.MSG_COUNT_TIMEOUT));
            }
            if (total > MAX_FULL_DOWNLOAD_ROWS
                    || (MappingService.isFilterOnly(request) && total > CodingVariantRepo.COUNT_CAP)) {
                downloadStatusService.increment("rejected");
                return ResponseEntity.badRequest().body(Map.of("error", DownloadStatusService.MSG_TOO_LARGE));
            }
        }

        request.setTimestamp(LocalDateTime.now());
        request.setFname(UUID.randomUUID().toString());

        String url = http.getRequestURL()
                .append("/")
                .append(request.getFname())
                .toString();
        request.setUrl(url);

        DownloadResponse response = downloadService.queueRequest(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fetch a finished download (zipped CSV)")
    @GetMapping(value = "/{filename}")
    @ResponseBody
    public ResponseEntity<?> downloadFile(
            @Parameter(description = "Job id from POST /download.", example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable("filename") String filename) {

        FileInputStream fileInputStream = downloadService.getFileResource(filename);
        if (fileInputStream == null)
            return new ResponseEntity<>("File not found", HttpStatus.NOT_FOUND);

        InputStreamResource resource = new InputStreamResource(fileInputStream);

        String contentType = "application/zip";
        String headerValue = "attachment; filename=" + filename + ".csv.zip";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);
    }

    @Operation(summary = "Check download status", description = "Body: list of job ids.")
    @PostMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, DownloadStatus>> downloadStatus(@RequestBody List<String> ids) {
        return new ResponseEntity<>(downloadService.getDownloadStatus(ids), HttpStatus.OK);
    }

    @Operation(hidden = true)
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DownloadStatsResponse> downloadStats() {
        DownloadStatsResponse.Cleanup cleanup = null;
        DownloadFileCleanupTask.LastSweep last = cleanupTask.getLastSweep();
        if (last != null) {
            cleanup = new DownloadStatsResponse.Cleanup(last.ranAt(), last.filesDeleted(), last.bytesFreed());
        }
        return ResponseEntity.ok(new DownloadStatsResponse(downloadStatusService.getCounters(), cleanup));
    }

}
