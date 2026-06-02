package uk.ac.ebi.protvar.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public class FileUtils {

  private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

  public static Path writeFile(Path tmpPath, MultipartFile file) throws IOException {
    Files.createDirectories(tmpPath);
    Path fileInTmpDir = Files.createTempFile(tmpPath, null, null);
    file.transferTo(fileInTmpDir);
    logger.info("File written {}", fileInTmpDir);
    return fileInTmpDir;
  }

  public static void zipFile(Path fileToZip, Path zipFilePath) throws IOException {
    if (Files.notExists(fileToZip)) {
      logger.warn("File to zip does not exist: " + fileToZip);
      return;
    }
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
      ZipEntry zipEntry = new ZipEntry(fileToZip.getFileName().toString());
      zos.putNextEntry(zipEntry);
      Files.copy(fileToZip, zos);
      zos.closeEntry();
    }
  }

  public static void tryDelete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      logger.warn("Couldn't delete file: "+ path.getFileName().toString());
    }
  }
}
