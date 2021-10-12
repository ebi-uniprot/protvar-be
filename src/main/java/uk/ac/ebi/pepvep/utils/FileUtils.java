package uk.ac.ebi.pepvep.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public class FileUtils {

  private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

  public static Path writeFile(MultipartFile file) throws IOException {
    Path fileInTmpDir = Files.createTempFile(null, null);
    file.transferTo(fileInTmpDir);
    logger.info("File written {}", fileInTmpDir);
    return fileInTmpDir;
  }
}
