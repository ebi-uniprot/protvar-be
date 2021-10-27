package uk.ac.ebi.pepvep.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import uk.ac.ebi.pepvep.builder.OptionBuilder;
import uk.ac.ebi.pepvep.fetcher.csv.CSVDataFetcher;
import uk.ac.ebi.pepvep.utils.FileUtils;


@Tag(name = "Download Variations",
  description = "Stream download results or send email. All below endpoints take same format of input and produces" +
    " same csv files. Currently support VCF format as input",
  externalDocs = @ExternalDocumentation(
    url = "https://github.com/ebi-uniprot/pepvep-be/blob/main/docs/csv-output-version1.md",
    description = "Result csv file & columns details"))
@RestController
@CrossOrigin
@AllArgsConstructor
public class DownloadController implements WebMvcConfigurer {
	private CSVDataFetcher csvDataFetcher;

  /**
   * Takes file and send results to user's email. Call to this endpoint will return once email sent to user
   * after all the processing.
   *
   * @param file       Full path to file (which you want to process) containing inputs mappings. Each line will contain
   *                   single mapping entry. Details about
   *                   <a href="https://github.com/ebi-uniprot/pepvep-be/blob/main/docs/vcf-format.md">VCF</a> format
   * @param email      Address where you want to receive results once processing it is done
   * @param jobName    Identifier to help you remember job, part of email subject
   * @param function   Do you want to include functional annotations in results
   * @param population Do you want to include population annotations in results
   * @param structure  Do you want to include structural annotations in results
   * @return <code>String</code> confirming you have received email
   * @throws Exception server side exception if something goes wrong
   */
	@PostMapping(value = "/email/process/file", produces = MediaType.APPLICATION_JSON_VALUE)
	public String upload(@RequestParam MultipartFile file, @RequestParam String email,
			@RequestParam(required = false) String jobName,
			@RequestParam(required = false, defaultValue = "false") boolean function,
			@RequestParam(required = false, defaultValue = "false") boolean population,
		  @RequestParam(required = false, defaultValue = "false") boolean structure) throws Exception {
		List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, population, structure);
		Path newFile = FileUtils.writeFile(file);
		csvDataFetcher.sendCSVResult(newFile, options, email, jobName);
		Files.delete(newFile);
		return "Your job submitted successfully, report will be sent to email " + email;
	}

  /**
   * Takes user data in body and send results to user's email. Call to this endpoint will return once email sent to user
   * after all the processing.
   *
   * @param inputs     Data which you want to analysis. List of String
   *                   ["19 1010539 G C","14 89993420 A/G", "10 87933147 rs7565837 C/T"]
   * @param email      Address where you want to receive results once processing it is done
   * @param jobName    Identifier to help you remember job, part of email subject
   * @param function   Do you want to include functional annotations in results
   * @param population Do you want to include population annotations in results
   * @param structure  Do you want to include structural annotations in results
   * @return <code>String</code> confirming you have received email
   * @throws Exception Server side exception if something goes wrong
   */
	@PostMapping(value = "/email/process/inputs", produces = MediaType.APPLICATION_JSON_VALUE)
	public String search(@RequestBody List<String> inputs,
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
   * Stream results back as response
   *
   * @param inputs     Data which you want to analysis. List of String as json document
   *                   ["19 1010539 G C","14 89993420 A/G", "10 87933147 rs7565837 C/T"]
   * @param function   Do you want to include functional annotations in results
   * @param population Do you want to include population annotations in results
   * @param structure  Do you want to include structural annotations in results
   * @throws Exception Server side exception - when something get wrong
   */
	@PostMapping(value = "/download/stream", produces = MediaType.APPLICATION_JSON_VALUE)
	public void download(@RequestBody List<String> inputs,
			@RequestParam(required = false, defaultValue = "false") boolean function,
			@RequestParam(required = false, defaultValue = "false") boolean population,
		  @RequestParam(required = false, defaultValue = "false") boolean structure,
			final HttpServletResponse response) throws Exception {
		List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, population, structure);
		response.addHeader("Content-Type", "application/csv");
		response.addHeader("Content-Disposition", "attachment; filename=pepvep.csv");
		csvDataFetcher.downloadCSVResult(inputs, options, response);
	}

}
