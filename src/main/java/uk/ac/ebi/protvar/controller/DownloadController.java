package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Download")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class DownloadController implements WebMvcConfigurer {
  private final DownloadService downloadService;

  @Operation(summary = "Submit a download request by known identifier or by cached user input ID (when type is CUSTOM_INPUT), " +
          "or single variant if type is not specified, with filtering and pagination options.")
  @PostMapping(value = "/download", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  // UI
  public ResponseEntity<?> download(HttpServletRequest httpRequest,
                                    @Valid // takes care of null or empty, no manual check needed
                                    @RequestBody DownloadRequest downloadRequest) {
    downloadRequest.setTimestamp(LocalDateTime.now());
    downloadRequest.buildAndSetFilename(); // <pref>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY][-advancedFilterHash]

    // Build URL for status or download
    String url = httpRequest.getRequestURL()
            .append("/")
            .append(downloadRequest.getFname())
            .toString();
    downloadRequest.setUrl(url);

    DownloadResponse response = downloadService.queueRequest(downloadRequest);
    return ResponseEntity.ok(response);
  }

  /**
   * Download results as CSV file.
   * pref is input ID, protein accession or hashCode of single variant string.
   * @param filename <pref>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY]
   * @return
   */
  @Operation(summary = "Download results file")
  @GetMapping(value = "/download/{filename}")
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
   * @param fs List of download files. The file name follows the pattern:
   *           <pref>[-PAGE][-PAGE_SIZE][-ASSEMBLY]
   * @return
   */
  @Operation(summary = "Check status of a list of download requests")
  @PostMapping(value = "/download/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, DownloadStatus>> downloadStatus(@RequestBody List<String> fs) {
    return new ResponseEntity<>(downloadService.getDownloadStatus(fs), HttpStatus.OK);
  }

}
