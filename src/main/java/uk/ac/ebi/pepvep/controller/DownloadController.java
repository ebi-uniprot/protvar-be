package uk.ac.ebi.pepvep.controller;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import uk.ac.ebi.pepvep.builder.OptionBuilder;
import uk.ac.ebi.pepvep.fetcher.csv.CSVDataFetcher;
import uk.ac.ebi.pepvep.utils.Constants;
import uk.ac.ebi.pepvep.utils.FileUtils;

@RestController
@RequestMapping("/download")
@CrossOrigin
@AllArgsConstructor
public class DownloadController implements WebMvcConfigurer {

	private final Logger logger = LoggerFactory.getLogger(DownloadController.class);
	private CSVDataFetcher csvDataFetcher;

	@PostMapping(value = "/file", produces = MediaType.APPLICATION_JSON_VALUE)
	public String upload(@RequestParam("file") MultipartFile file,
			@RequestParam(name = "email", required = false, defaultValue = "") String email,
			@RequestParam(name = "jobName", required = false, defaultValue = "") String jobName,
			@RequestParam(name = "function", required = false, defaultValue = "false") boolean function,
			@RequestParam(name = "variation", required = false, defaultValue = "false") boolean variation,
		  @RequestParam(name = "structure", required = false, defaultValue = "false") boolean structure) throws Exception {
		List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, variation, structure);
		String newFile;
		if (file != null) {
			newFile = FileUtils.writeFile(file, Constants.FILE_PATH);
			csvDataFetcher.sendCSVResult(newFile, options, email, jobName);

		}
		return "Your job submitted successfully, report will be sent to email " + email;
	}

	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
	public String search(@RequestBody List<String> inputs,
			@RequestParam(name = "email", required = false, defaultValue = "") String email,
			@RequestParam(name = "jobName", required = false, defaultValue = "") String jobName,
			@RequestParam(name = "function", required = false, defaultValue = "false") boolean function,
			@RequestParam(name = "variation", required = false, defaultValue = "false") boolean variation,
		  @RequestParam(name = "structure", required = false, defaultValue = "false") boolean structure) throws Exception {
		List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, variation, structure);
		csvDataFetcher.sendCSVResult(inputs, options, email, jobName);

		return "Your job submitted successfully, report will be sent to email " + email;
	}

	@PostMapping(value = "/download", produces = MediaType.APPLICATION_JSON_VALUE)
	public void download(@RequestBody List<String> inputs,
			@RequestParam(name = "function", required = false, defaultValue = "false") boolean function,
			@RequestParam(name = "variation", required = false, defaultValue = "false") boolean variation,
		  @RequestParam(name = "structure", required = false, defaultValue = "false") boolean structure,
			final HttpServletResponse response) throws Exception {
		List<OptionBuilder.OPTIONS> options = OptionBuilder.build(function, variation, structure);
		response.addHeader("Content-Type", "application/csv");
		response.addHeader("Content-Disposition", "attachment; filename=pepvep.csv");
		csvDataFetcher.downloadCSVResult(inputs, options, response);
	}

}
