package uk.ac.ebi.protvar.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestHelper {
  static final int MINIMUM_ENTRIES_IN_ALL_FILES = 4548;
  private static final int MAPPING_FILES_COUNT = 18;

  public static String getRandomMappingsJson(int count) {
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return ow.writeValueAsString(getRandomMappings(count));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * From Random file get consecutive records after skipping random numbers
   */
  public static List<String> getRandomMappings(int count) {
    if (count > MINIMUM_ENTRIES_IN_ALL_FILES)
      count = MINIMUM_ENTRIES_IN_ALL_FILES;
    int inclusiveBound = MINIMUM_ENTRIES_IN_ALL_FILES + 1 - count;
    int skipCount = new Random().nextInt(inclusiveBound);
    try (Stream<String> lines = Files.lines(getRandomMappingFile())) {
      return lines.skip(skipCount).limit(count).collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Path getRandomMappingFile() {
    int zeroBasedRandomFileNumberUpToCount = new Random().nextInt(MAPPING_FILES_COUNT);
    int actualFileNumber = zeroBasedRandomFileNumberUpToCount + 1;
    var fileName = "gatling/mapping" + actualFileNumber + ".tsv";
    return Paths.get(Objects.requireNonNull(TestHelper.class.getClassLoader().getResource(fileName)).getFile());
  }
}
