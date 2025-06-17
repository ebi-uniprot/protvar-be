package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.ac.ebi.protvar.constants.PagedMapping;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.UserInputRequest;
import uk.ac.ebi.protvar.service.UserInputService;
import uk.ac.ebi.protvar.types.IdentifierType;
import uk.ac.ebi.protvar.model.response.DownloadResponse;
import uk.ac.ebi.protvar.model.response.DownloadStatus;
import uk.ac.ebi.protvar.service.DownloadService;
import uk.ac.ebi.protvar.utils.ChecksumUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Download")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class DownloadController implements WebMvcConfigurer {
  private static final Logger LOGGER = LoggerFactory.getLogger(DownloadController.class);

  // Download request using
  // -file                  : return all
  // -text (string inputs)  : return all
  // -inputId               : return specific page/pageSize

  private final UserInputService userInputService;
  private final DownloadService downloadService;

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
  // API only
  @Operation(summary = "Submit download request for the file input and provided parameters")
  @PostMapping(value = "/download/fileInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
                                    @RequestParam MultipartFile file,
                                    @RequestParam(required = false, defaultValue = "false") boolean function,
                                    @RequestParam(required = false, defaultValue = "false") boolean population,
                                    @RequestParam(required = false, defaultValue = "false") boolean structure,
                                    @RequestParam(required = false, defaultValue = "AUTO") String assembly,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String jobName) throws Exception {
    try {
      String id = userInputService.processInput(UserInputRequest.builder()
              .rawInput(new String(file.getBytes()))
              .assembly(assembly)
              .build());
      // Create a download request
      DownloadRequest downloadRequest = newDownloadRequest(IdentifierType.CUSTOM_INPUT, id, function, population, structure,
              assembly, email, jobName);

      // <id>[-fun][-pop][-str][-ASSEMBLY]
      String filename = getFilename(id, function, population, structure, null, null, assembly);
      downloadRequest.setFname(filename);
      String url = request.getRequestURL().toString().replace("fileInput", filename);
      downloadRequest.setUrl(url);

      DownloadResponse response = downloadService.queueRequest(downloadRequest);
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (IOException ex) {
      return ResponseEntity.badRequest().body("Submitted file error");
    }
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
  // API only
  @Operation(summary = "Submit download request for the list of inputs and provided parameters")
  @PostMapping(value = "/download/textInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
          @RequestBody List<String> inputs,
          @RequestParam(required = false, defaultValue = "false") boolean function,
          @RequestParam(required = false, defaultValue = "false") boolean population,
          @RequestParam(required = false, defaultValue = "false") boolean structure,
          @RequestParam(required = false, defaultValue = "AUTO") String assembly,
          @RequestParam(required = false) String email,
          @RequestParam(required = false) String jobName) {
    String id = userInputService.processInput(UserInputRequest.builder()
            .rawInput(String.join(System.lineSeparator(), inputs))
            .assembly(assembly)
            .build());
    DownloadRequest downloadRequest = newDownloadRequest(IdentifierType.CUSTOM_INPUT, id, function, population, structure,
            assembly, email, jobName);

    // <id>[-fun][-pop][-str][-ASSEMBLY]
    String filename = getFilename(id, function, population, structure, null, null, assembly);
    downloadRequest.setFname(filename);
    String url = request.getRequestURL().toString().replace("textInput", filename);
    downloadRequest.setUrl(url);

    DownloadResponse response = downloadService.queueRequest(downloadRequest);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Operation(summary = "Submit a download request for the input, which may be an identifier of a specified type " +
          "(e.g., UNIPROT, GENE, etc), or a single variant. If the type is not specified (defaults to null), " +
          "the input is interpreted as a single variant. Optional parameters include page and pageSize. " +
          "If no page is specified, the entire input is processed.")
  @PostMapping(value = "/download", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  // UI
  public ResponseEntity<?> download(HttpServletRequest request,
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
                  description = "The identifier or single variant input to generate download for."
          )
          @RequestBody String input,
          @RequestParam(required = false) IdentifierType type,
          @Parameter(description = "The page number to retrieve. If not specified, download file is generated for all inputs.")
          @RequestParam(required = false) Integer page,
          @Parameter(description = "The number of results per page.")
          @RequestParam(required = false) Integer pageSize,
          @RequestParam(required = false, defaultValue = "false") boolean function,
          @RequestParam(required = false, defaultValue = "false") boolean population,
          @RequestParam(required = false, defaultValue = "false") boolean structure,
          @RequestParam(required = false, defaultValue = "AUTO") String assembly,
          @RequestParam(required = false) String email,
          @RequestParam(required = false) String jobName) {
    if (input == null || input.trim().isEmpty()) {
      return ResponseEntity.badRequest().body("Input must not be null or empty");
    }

    DownloadRequest downloadRequest = newDownloadRequest(type, input, function, population, structure,
            assembly, email, jobName);

    downloadRequest.setPage(page);
    downloadRequest.setPageSize(pageSize);
    String pref = type == null ? ChecksumUtils.checksum(input.getBytes()) : sanitizeForFilename(input);

    // <pref>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY]
    String filename = getFilename(pref, function, population, structure, page, pageSize, assembly);
    downloadRequest.setFname(filename);
    String url = request.getRequestURL().append("/").append(filename).toString();
    downloadRequest.setUrl(url);
    DownloadResponse response = downloadService.queueRequest(downloadRequest);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  public static String sanitizeForFilename(String input) {
    if (input == null || input.isEmpty()) {
      return "unknown";
    }

    // Replace characters that are problematic in filenames
    return input.replaceAll("[\\\\/:*?\"<>|]", "_");
  }

  private String getFilename(String pref,
                             boolean function, boolean population, boolean structure,
                             Integer page, Integer pageSize, String assembly) {

    String filename = pref; // <pref>[OPTIONS]

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

  private DownloadRequest newDownloadRequest(IdentifierType type, String input, boolean function, boolean population, boolean structure,
                                             String assembly, String email, String jobName) {
    DownloadRequest downloadRequest = new DownloadRequest();
    downloadRequest.setTimestamp(LocalDateTime.now());
    downloadRequest.setType(type);
    downloadRequest.setInput(input);
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
