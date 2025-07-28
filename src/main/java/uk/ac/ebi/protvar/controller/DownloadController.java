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
import uk.ac.ebi.protvar.types.InputType;
import uk.ac.ebi.protvar.utils.DownloadFileUtil;
import uk.ac.ebi.protvar.utils.InputTypeResolver;

import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Download")
@RestController
@RequestMapping("/download") // Base path for all download-related endpoints
@CrossOrigin
@RequiredArgsConstructor
public class DownloadController implements WebMvcConfigurer {

    private final static String SUMMARY = """
            Submit a download request. If no type is specified, the input is treated as a single variant.
            """;
    private final DownloadService downloadService;

    @Operation(summary = SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> downloadGet(@Valid @RequestBody
                                         DownloadRequest request,
                                         HttpServletRequest http) {
        // Valid takes care of null or empty, no manual check needed?
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
     * Handle download request, setting timestamp and filename.
     * Builds the URL for status or download.
     *
     * @param request DownloadRequest containing parameters
     * @param http    HttpServletRequest to build the URL
     * @return ResponseEntity with DownloadResponse or error
     */
    public ResponseEntity<?> handleDownload(DownloadRequest request, HttpServletRequest http) {
        InputType providedType = request.getType(); // user-provided, may be null
        InputType resolved = InputTypeResolver.resolve(request.getInput());

        if (providedType != null && !providedType.equals(resolved)) {
            return ResponseEntity.badRequest().body(
                    String.format("Input type '%s' does not match resolved type '%s'.", providedType, resolved)
            );
        }
        if (resolved == null) {
            return ResponseEntity.badRequest().body("Unable to resolve input type from provided input.");
        }

        request.setType(resolved);

        if (request.getInput() != null &&
                (resolved != InputType.PDB && resolved != InputType.INPUT_ID)) { // todo: move normalizing case in SQL query for consistency
            request.setInput(request.getInput().toUpperCase());
        }

        request.setTimestamp(LocalDateTime.now());
        request.setFname(DownloadFileUtil.buildFilename(request));

        // Build URL for status or download
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
            @Parameter(example = "cc3b5e1a21fd") @PathVariable("filename") String filename) {

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

    /**
     * Check download status.
     *
     * @param fs List of download files. The file name follows the pattern:
     *           <prefix>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY][-filterHash]
     * @return
     */
    @Operation(summary = "Check status of a list of download requests")
    @PostMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, DownloadStatus>> downloadStatus(@RequestBody List<String> fs) {
        return new ResponseEntity<>(downloadService.getDownloadStatus(fs), HttpStatus.OK);
    }

}
