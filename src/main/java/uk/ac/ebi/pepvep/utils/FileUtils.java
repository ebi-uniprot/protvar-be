package uk.ac.ebi.pepvep.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public class FileUtils {

	private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

	public static Path writeFile(MultipartFile file, String filePath) throws IOException {
		String timeStamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
		String fileName = file.getOriginalFilename() + "_" + timeStamp;
		Path path = Paths.get(filePath + fileName);
		file.transferTo(path);
		logger.info("File written {}", fileName);
		return path;
	}
}
