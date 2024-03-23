package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.response.DownloadResponse;
import uk.ac.ebi.protvar.service.DownloadService;
import uk.ac.ebi.protvar.utils.FileUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Tag(name = "Download")
@RestController
@CrossOrigin
@AllArgsConstructor
public class DownloadController implements WebMvcConfigurer {

  @Autowired
  private DownloadService downloadService;

  /**
   * Submit a download request using file input. The download process is launched in the background.
   * If an email address is specified, a notification is sent when the result file is ready to be
   * downloaded.
   *
   * @param file
   * @param email
   * @param jobName
   * @param function
   * @param population
   * @param structure
   * @return response has the job ID to check status
   * @throws Exception
   */
  @Operation(summary = "Download request using file input")
  @PostMapping(value = "/download/fileInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
                                    @RequestParam MultipartFile file,
                                    @RequestParam(required = false, defaultValue = "false") boolean function,
                                    @RequestParam(required = false, defaultValue = "false") boolean population,
                                    @RequestParam(required = false, defaultValue = "false") boolean structure,
                                    @RequestParam(required = false) String assembly,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String jobName) throws Exception {
    Path newFile = FileUtils.writeFile(downloadService.tmpPath(), file);
    UUID id = UUID.randomUUID();
    DownloadRequest downloadRequest =
            new DownloadRequest(id, LocalDateTime.now(),
                    newFile, null,
                    function, population, structure,
                    assembly, email, jobName,
                    request.getRequestURL().toString().replace("fileInput", id.toString()));
    DownloadResponse response = downloadService.queueRequest(downloadRequest);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  /**
   * Submit a download request using text input.
   *
   * @param inputs
   * @param email
   * @param jobName
   * @param function
   * @param population
   * @param structure
   * @return response has the job ID to check status
   */
  @Operation(summary = "Download request using text input")
  @PostMapping(value = "/download/textInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
          @RequestBody List<String> inputs,
          @RequestParam(required = false, defaultValue = "false") boolean function,
          @RequestParam(required = false, defaultValue = "false") boolean population,
          @RequestParam(required = false, defaultValue = "false") boolean structure,
          @RequestParam(required = false) String assembly,
          @RequestParam(required = false) String email,
          @RequestParam(required = false) String jobName) {
    UUID id = UUID.randomUUID();
    DownloadRequest downloadRequest =
            new DownloadRequest(id, LocalDateTime.now(),
                    null, inputs,
                    function, population, structure,
                    assembly, email, jobName,
                    request.getRequestURL().toString().replace("textInput", id.toString()));
    DownloadResponse response = downloadService.queueRequest(downloadRequest);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  /**
   * Download results as CSV file.
   * @param id
   * @return
   */
  @Operation(summary = "Download results file")
  @GetMapping(value = "/download/{id}")
  @ResponseBody
  public ResponseEntity<?> downloadFile(
          @Parameter(example = "cc3b5e1a21fd") @PathVariable("id") String id) {

    FileInputStream fileInputStream = downloadService.getFileResource(id);
    if (fileInputStream == null)
      return new ResponseEntity<>("File not found", HttpStatus.NOT_FOUND);

    InputStreamResource resource = new InputStreamResource(fileInputStream);

      String contentType = "application/zip";
      String headerValue = "attachment; filename=" + id + ".csv.zip";

      return ResponseEntity.ok()
              .contentType(MediaType.parseMediaType(contentType))
              .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
              .body(resource);
  }

  /**
   * Check download status.
   * @param ids List of job/download IDs
   * @return
   */
  @Operation(summary = "Check status of a list of download requests")
  @PostMapping(value = "/download/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String,Integer>> downloadStatus(@RequestBody List<String> ids) {
    return new ResponseEntity<>(downloadService.getDownloadStatus(ids), HttpStatus.OK);
  }

}
