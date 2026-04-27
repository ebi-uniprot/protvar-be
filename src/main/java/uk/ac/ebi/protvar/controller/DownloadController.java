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
import uk.ac.ebi.protvar.model.response.DownloadStatus;
import uk.ac.ebi.protvar.service.DownloadService;
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

    private final static String SUMMARY = "Submit a download request.";
    private final DownloadService downloadService;

    @Operation(summary = SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> downloadGet(@Valid @RequestBody
                                         DownloadRequest request,
                                         HttpServletRequest http) {
        return handleDownload(request, http);
    }

    @Operation(summary = SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> downloadPost(@Valid @RequestBody @ModelAttribute
                                          DownloadRequest request,
                                          HttpServletRequest http) {
        return handleDownload(request, http);
    }

    /**
     * Handle download request: validate, allocate UUID job ID, write PENDING status, queue.
     */
    public ResponseEntity<?> handleDownload(DownloadRequest request, HttpServletRequest http) {
        Optional<String> validationError = MappingRequestValidator.validate(request);
        if (validationError.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError.get()));
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

    @Operation(summary = "Download results file")
    @GetMapping(value = "/{filename}")
    @ResponseBody
    public ResponseEntity<?> downloadFile(
            @Parameter(example = "550e8400-e29b-41d4-a716-446655440000") @PathVariable("filename") String filename) {

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

    @Operation(summary = "Check status of a list of download requests")
    @PostMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, DownloadStatus>> downloadStatus(@RequestBody List<String> ids) {
        return new ResponseEntity<>(downloadService.getDownloadStatus(ids), HttpStatus.OK);
    }

}
