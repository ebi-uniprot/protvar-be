package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CSVDataFetcher;
import uk.ac.ebi.protvar.service.DownloadService;
import uk.ac.ebi.protvar.utils.FileUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.nio.file.Files;
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

  private DownloadService downloadService;

  /**
   * Users submit a path to a genomic coordinates variants file in VCF format on their local machine and annotations
   * are returned via email. The call to this endpoint will return once processing has been completed and the results
   * email has been sent.
   *
   * @param file       Full path to VCF format variants file which you would like to process Each should contain a
   *                   single variant. Details about VCF format and variations accepted can be found
   *                   <a href="https://github.com/ebi-uniprot/protvar-be/blob/main/docs/vcf-format.md" target='_new'>here</a>
   * @param email      Email address where you want to receive your results once processing is complete
   * @param jobName    Identifier to help you track your jobs. This will form part of the returned email subject
   * @param function   Include functional annotations in results
   * @param population Include population annotations (residue co-located variants and disease associations) in results
   * @param structure  Include structural annotations in results
   * @return <code>String</code> confirming you have received email
   * @throws Exception server side exception if something goes wrong
   */
  @Operation(summary = "– upload a VCF format variants file and retrieve results via email")
  @PostMapping(value = "/email/process/file", produces = MediaType.APPLICATION_JSON_VALUE)
  public String upload(@RequestParam MultipartFile file, @RequestParam String email,
                       @RequestParam(required = false) String jobName,
                       @RequestParam(required = false, defaultValue = "false") boolean function,
                       @RequestParam(required = false, defaultValue = "false") boolean population,
                       @RequestParam(required = false, defaultValue = "false") boolean structure) throws Exception {
    List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, population, structure);
    Path newFile = FileUtils.writeFile(file);
    try {
      csvDataFetcher.sendCSVResult(newFile, options, email, jobName);
    } finally {
      Files.delete(newFile);
    }
    return "Your job submitted successfully, report will be sent to email " + email;
  }

  /**
   * Users submit a list of genomic coordinate variant inputs in VCF format and receive annotation results via email.
   * The call to this endpoint will return once processing has been completed and the results email has been sent
   *
   * @param inputs     Variants which you wish to retrieve annotations for in json string array format
   * @param email      Email address where you want to receive your results once processing is complete
   * @param jobName    Identifier to help you track your jobs. This will form part of the returned email subject
   * @param function   Include functional annotations in results
   * @param population Include population annotations (residue co-located variants and disease associations) in results
   * @param structure  Include structural annotations in results
   * @return <code>String</code> confirming the results email has been sent
   * @throws Exception Server side exception if something goes wrong
   */
  @Operation(summary = "– submit a list of variants and retrieve results via email")
  @PostMapping(value = "/email/process/inputs", produces = MediaType.APPLICATION_JSON_VALUE)
  public String search(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
    @ExampleObject(value = "[\"19 1010539 G C\",\"14 89993420 A/G\", \"10 87933147 rs7565837 C/T\"]"))})
    @RequestBody List<String> inputs,
    @RequestParam String email,
    @RequestParam(required = false) String jobName,
    @RequestParam(required = false, defaultValue = "false") boolean function,
    @RequestParam(required = false, defaultValue = "false") boolean population,
    @RequestParam(required = false, defaultValue = "false") boolean structure) throws Exception {
    List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, population, structure);
    csvDataFetcher.sendCSVResult(inputs, options, email, jobName);
    return "Your job submitted successfully, report will be sent to email " + email;
  }

  /**
   * Stream results back as response. (You must press download when trying it here in the browser)
   *
   * @param inputs     Variants which you wish to retrieve annotations for in json string array format (example shown below):
   * @param function   Include functional annotations in results
   * @param population Include population annotations (residue co-located variants and disease associations) in results
   * @param structure  Include structural annotations in results
   * @throws Exception Server side exception - when something get wrong
   */
  @Operation(summary = "– stream mapping and annotation results back as a response")
  @PostMapping(value = "/download/stream", produces = MediaType.APPLICATION_JSON_VALUE)
  public void download(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
    @ExampleObject(value = "[\"19 1010539 G C\",\"14 89993420 A/G\", \"10 87933147 rs7565837 C/T\"]"))})
    @RequestBody List<String> inputs,
    @RequestParam(required = false, defaultValue = "false") boolean function,
    @RequestParam(required = false, defaultValue = "false") boolean population,
    @RequestParam(required = false, defaultValue = "false") boolean structure,
    final HttpServletResponse response) throws Exception {
    List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, population, structure);
    response.addHeader("Content-Type", "application/csv");
    response.addHeader("Content-Disposition", "attachment; filename=ProtVar.csv");
    csvDataFetcher.downloadCSVResult(inputs, options, response);
  }

  @GetMapping(value = "/download/{id}")
  @ResponseBody
  public ResponseEntity<?> downloadFile(
          @Parameter(example = "cc3b5e1a21fd") @PathVariable("id") String id) {

    FileInputStream fileInputStream = downloadService.getFileResource(id);
    if (fileInputStream == null)
      return new ResponseEntity<>("File not found", HttpStatus.NOT_FOUND);

    InputStreamResource resource = new InputStreamResource(fileInputStream);

      String contentType = "application/csv";
      String headerValue = "attachment; filename=" + id + ".csv";

      return ResponseEntity.ok()
              .contentType(MediaType.parseMediaType(contentType))
              .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
              .body(resource);
  }

  @PostMapping(value = "/download/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String,Integer>> downloadStatus(@RequestBody List<String> ids) {
    return new ResponseEntity<>(downloadService.getDownloadStatus(ids), HttpStatus.OK);
  }

}
