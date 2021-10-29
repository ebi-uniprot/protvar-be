package uk.ac.ebi.protvar.fetcher.csv;

import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CSVZipWriter implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(CSVZipWriter.class);
  public final Path path;
  public final CSVWriter writer;
  private final ZipOutputStream zos;

  public CSVZipWriter() throws IOException {
    logger.debug("Using temp directory: " + System.getProperty("java.io.tmpdir"));
    path = Files.createTempFile("result", ".zip");
    logger.debug("Result File created at : " + path);
    zos = new ZipOutputStream(Files.newOutputStream(path));
    ZipEntry entry = new ZipEntry("result-ProtVar.csv");
    zos.putNextEntry(entry);
    writer = new CSVWriter(new OutputStreamWriter(zos));
  }

  @Override
  public void close() throws IOException {
    writer.flush();
    zos.closeEntry();
    writer.close();
    zos.close();
  }
}
