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

  public static Path writeFile(MultipartFile file) throws IOException {
    Path fileInTmpDir = Files.createTempFile(null, null);
    file.transferTo(fileInTmpDir);
    logger.info("File written {}", fileInTmpDir);
    return fileInTmpDir;
  }

  public static void zipFile(String in, String out) throws Exception {
    File fileToZip = new File(in);

    try(FileOutputStream fos = new FileOutputStream(out);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        FileInputStream fis = new FileInputStream(fileToZip)) {

      ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
      zipOut.putNextEntry(zipEntry);

      byte[] bytes = new byte[1024];
      int length;
      while ((length = fis.read(bytes)) >= 0) {
        zipOut.write(bytes, 0, length);
      }
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
