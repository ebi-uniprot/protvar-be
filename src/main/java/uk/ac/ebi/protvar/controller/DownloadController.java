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
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CSVDataFetcher;
import uk.ac.ebi.protvar.model.response.Download;
import uk.ac.ebi.protvar.service.DownloadService;
import uk.ac.ebi.protvar.utils.FileUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


@Tag(name = "Generate Variant Annotation Files",
  description = "Retrieve annotations for a list of protein coding variants.\n\n\n" +
    "All three endpoints in this section require a list of genomic variant inputs and all return a .csv file" +
    " in the same format as the “download” service in the ProtVar website. A description of the columns in the " +
    "file can be found <a href='https://github.com/ebi-uniprot/protvar-be/blob/main/docs/csv-output-version1.md' target='_new'>here</a>." +
    " The user can choose which types of annotations are required.\n\n\n" +
    "<strong>Please note that large lists where all annotations are requested will take a long time to respond.<strong>")
@RestController
@CrossOrigin
@AllArgsConstructor
public class DownloadController implements WebMvcConfigurer {
  private CSVDataFetcher csvDataFetcher;

  @Autowired
  private DownloadService downloadService;

  /**
   * Submit a download request using file input. This endpoint returns a job ID, and the download process is launched
   * in the background. If an email address is specified, a notification is sent when the result file is ready to be
   * downloaded. The download status can be checked using the `/download/status` endpoint. The result file can be
   * downloaded using the `/download/{id}` endpoint with the job ID from this request.
   *
   * @param file
   * @param email
   * @param jobName
   * @param function
   * @param population
   * @param structure
   * @return
   * @throws Exception
   */
  @Operation(summary = "– submit mappings download request using file input.")
  @PostMapping(value = "/download/fileInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
                                    @RequestParam MultipartFile file,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String jobName,
                                    @RequestParam(required = false, defaultValue = "false") boolean function,
                                    @RequestParam(required = false, defaultValue = "false") boolean population,
                                    @RequestParam(required = false, defaultValue = "false") boolean structure,
                                    @RequestParam(required = false) String assembly) throws Exception {
    List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, population, structure);
    Download download = downloadService.newDownload("FILE");
    download.setJobName(jobName);
    String downloadUrl = request.getRequestURL().toString().replaceAll("fileInput", download.getDownloadId());
    download.setUrl(downloadUrl);
    Path newFile = FileUtils.writeFile(file);
    csvDataFetcher.writeCSVResult(newFile, assembly, options, email, jobName, download);
    return new ResponseEntity<>(download, HttpStatus.OK);
  }

  /**
   * Submit a download request using text input. This endpoint returns a job ID, and the download process is launched
   * in the background. If an email address is specified, a notification is sent when the result file is ready to be
   * downloaded. The download status can be checked using the `/download/status` endpoint. The result file can be
   * downloaded using the `/download/{id}` endpoint with the job ID from this request.
   *
   * @param inputs
   * @param email
   * @param jobName
   * @param function
   * @param population
   * @param structure
   * @return
   * @throws Exception
   */
  @Operation(summary = "– download mappings using file or text input.")
  @PostMapping(value = "/download/textInput", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> download(HttpServletRequest request,
          @RequestBody List<String> inputs,
          @RequestParam(required = false) String email,
          @RequestParam(required = false) String jobName,
          @RequestParam(required = false, defaultValue = "false") boolean function,
          @RequestParam(required = false, defaultValue = "false") boolean population,
          @RequestParam(required = false, defaultValue = "false") boolean structure,
          @RequestParam(required = false) String assembly) throws Exception {
    List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, population, structure);
    Download download = downloadService.newDownload("TEXT");
    download.setJobName(jobName);
    String downloadUrl = request.getRequestURL().toString().replaceAll("textInput", download.getDownloadId());
    download.setUrl(downloadUrl);
    csvDataFetcher.writeCSVResult(inputs, assembly, options, email, jobName, download);
    return new ResponseEntity<>(download, HttpStatus.OK);
  }

  /**
   * Download results as CSV file.
   * @param id
   * @return
   */
  @Operation(summary = "– download results using job/download `id`.")
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
   * @param ids a list of job/download IDs
   * @return
   */
  @Operation(summary = "– Check the download status of a list of job/download IDs.")
  @PostMapping(value = "/download/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String,Integer>> downloadStatus(@RequestBody List<String> ids) {
    return new ResponseEntity<>(downloadService.getDownloadStatus(ids), HttpStatus.OK);
  }

}
