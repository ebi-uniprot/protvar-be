package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestHelperTest {
  @Test
  void canGetMappingFilePath() {
    assertNotNull(TestHelper.getRandomMappingFile());
  }

  @Test
  void canGetMappings() {
    assertEquals(10, TestHelper.getRandomMappings(10).size());
  }

  @Test
  void canGetMappingsMax() {
    assertEquals(TestHelper.MINIMUM_ENTRIES_IN_ALL_FILES,
      TestHelper.getRandomMappings(TestHelper.MINIMUM_ENTRIES_IN_ALL_FILES).size());
  }

  @Test
  void canGetMappingsJson() {
    String jsonArray = TestHelper.getRandomMappingsJson(4);
    assertTrue(jsonArray.startsWith("["));
    assertTrue(jsonArray.endsWith("]"));
    assertEquals(4, jsonArray.split(",").length);
  }
}
