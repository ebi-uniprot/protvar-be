package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.ac.ebi.protvar.cache.InputCache;
import uk.ac.ebi.protvar.config.PagedMapping;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.ResultType;
import uk.ac.ebi.protvar.model.response.DownloadResponse;
import uk.ac.ebi.protvar.model.response.DownloadStatus;
import uk.ac.ebi.protvar.service.DownloadService;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Download")
@RestController
@CrossOrigin
@AllArgsConstructor
/**
 * Download request using:
 * 1. file input            -> returns all
 * 2. string (text) inputs  -> returns all
 * 3. id input              -> returns specified page num (and page size)
 *
 */
public class DownloadController implements WebMvcConfigurer {

  private InputCache inputCache;
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
   * @return response contains an ID to check status
   * @throws Exception
   */
  @Operation(summary = "Submit download request for the file input and provided parameters")
  @PostMapping(value = "/download/fileInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
                                    @RequestParam MultipartFile file,
                                    @RequestParam(required = false, defaultValue = "false") boolean function,
                                    @RequestParam(required = false, defaultValue = "false") boolean population,
                                    @RequestParam(required = false, defaultValue = "false") boolean structure,
                                    @RequestParam(required = false) String assembly,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String jobName) throws Exception {
    String id = inputCache.cacheFileInput(file);
    DownloadRequest downloadRequest = newDownloadRequest(id, ResultType.CUSTOM_INPUT, function, population, structure,
            assembly, email, jobName);

    // <id>[-fun][-pop][-str][-ASSEMBLY]
    String filename = getFilename(id, function, population, structure, null, null, assembly);
    downloadRequest.setFname(filename);
    String url = request.getRequestURL().toString().replace("fileInput", filename);
    downloadRequest.setUrl(url);

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
   * @return response contains an ID to check status
   */
  @Operation(summary = "Submit download request for the list of inputs and provided parameters")
  @PostMapping(value = "/download/textInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
          @RequestBody List<String> inputs,
          @RequestParam(required = false, defaultValue = "false") boolean function,
          @RequestParam(required = false, defaultValue = "false") boolean population,
          @RequestParam(required = false, defaultValue = "false") boolean structure,
          @RequestParam(required = false) String assembly,
          @RequestParam(required = false) String email,
          @RequestParam(required = false) String jobName) {
    String id = inputCache.cacheTextInput(String.join(System.lineSeparator(), inputs));
    DownloadRequest downloadRequest = newDownloadRequest(id, ResultType.CUSTOM_INPUT, function, population, structure,
            assembly, email, jobName);

    // <id>[-fun][-pop][-str][-ASSEMBLY]
    String filename = getFilename(id, function, population, structure, null, null, assembly);
    downloadRequest.setFname(filename);
    String url = request.getRequestURL().toString().replace("textInput", filename);
    downloadRequest.setUrl(url);

    DownloadResponse response = downloadService.queueRequest(downloadRequest);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Operation(summary = "Submit download request for the input ID and provided parameters including page and pageSize. " +
          "If no page is specified, the full original input is processed.")
  @PostMapping(value = "/download", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ResponseEntity<?> download(HttpServletRequest request,
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
                  description = "The unique ID of the input to generate download for."
          )
          @RequestBody String id,
          @Parameter(description = "The page number to retrieve. If not specified, download file is generated for all inputs.")
          @RequestParam(required = false, defaultValue = "CUSTOM_INPUT") ResultType type,
          @RequestParam(required = false) Integer page,
          @Parameter(description = "The number of results per page.")
          @RequestParam(required = false) Integer pageSize,
          @RequestParam(required = false, defaultValue = "false") boolean function,
          @RequestParam(required = false, defaultValue = "false") boolean population,
          @RequestParam(required = false, defaultValue = "false") boolean structure,
          @RequestParam(required = false) String assembly,
          @RequestParam(required = false) String email,
          @RequestParam(required = false) String jobName) {
    DownloadRequest downloadRequest = newDownloadRequest(id, type, function, population, structure,
            assembly, email, jobName);

    downloadRequest.setPage(page);
    downloadRequest.setPageSize(pageSize);
    // <id>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY]
    String filename = getFilename(id, function, population, structure, page, pageSize, assembly);
    downloadRequest.setFname(filename);
    String url = request.getRequestURL().append("/").append(filename).toString();
    downloadRequest.setUrl(url);
    DownloadResponse response = downloadService.queueRequest(downloadRequest);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private String getFilename(String id,
                             boolean function, boolean population, boolean structure,
                             Integer page, Integer pageSize, String assembly) {

    String filename = id; // <id>[OPTIONS]

    if (function)
      filename += "-fun";
    if (population)
      filename += "-pop";
    if (structure)
      filename += "-str";

    if (page == null && pageSize == null) {
      // page options not provided, assume default
      // do not append page/pageSize to filename (return ALL input mappings)
    } else if ((page != null && page == PagedMapping.DEFAULT_PAGE) &&
            (pageSize != null && pageSize == PagedMapping.DEFAULT_PAGE_SIZE)) {
      // default page options
      // do not append page/pageSize to filename (return ALL input mappings)
    } else {
      filename += (page == null ? "" : "-" + page);
      filename += (pageSize == null ? "" : "-" + pageSize);
    }

    if (assembly == null || assembly.equals("AUTO")) {
      // default assembly
      // do not append assembly
    } else {
      filename += "-" + assembly;
    }
    return filename;
  }

  private DownloadRequest newDownloadRequest(String id, ResultType type, boolean function, boolean population, boolean structure,
                                                 String assembly, String email, String jobName) {
    DownloadRequest downloadRequest = new DownloadRequest();
    downloadRequest.setTimestamp(LocalDateTime.now());
    downloadRequest.setId(id);
    if (type != null)
      downloadRequest.setType(type);
    downloadRequest.setFunction(function);
    downloadRequest.setPopulation(population);
    downloadRequest.setStructure(structure);
    downloadRequest.setAssembly(assembly);
    downloadRequest.setEmail(email);
    downloadRequest.setJobName(jobName);
    return downloadRequest;
  }

  /**
   * Download results as CSV file.
   * @param filename <id>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY]
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
   *           <id>[-PAGE][-PAGE_SIZE][-ASSEMBLY]
   * @return
   */
  @Operation(summary = "Check status of a list of download requests")
  @PostMapping(value = "/download/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, DownloadStatus>> downloadStatus(@RequestBody List<String> fs) {
    return new ResponseEntity<>(downloadService.getDownloadStatus(fs), HttpStatus.OK);
  }

}
