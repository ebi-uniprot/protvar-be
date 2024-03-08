package uk.ac.ebi.protvar.input.format.genomic;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.utils.Constants;

import static org.junit.jupiter.api.Assertions.*;

class VCFTest {

  // test for
  // chr pos id ref alt[NO SPACE]
  // chr pos id ref alt[SPACE]
  // chr pos id ref alt[SPACE+ANY OTHER CHAR]
  // chr pos id ref alt[ANY OTHER CHAR]
  @ParameterizedTest
  @ValueSource(strings = {"y 987654321 rs123 t g", "y 987654321 rs123 t g ",
          "y 987654321 rs123 t g description;",
    "y 987654321 rs123 t g 100 PASS AA=T|||;AC=1;AF=0"})
  void test_inputWithDiffEndOptions(String inputStr) {
    VCF userInput = VCF.parse(inputStr);
    assertParsedInput(true, inputStr, "Y", 987654321, "rs123", "T", "G", "Y-987654321", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"y 987654321 . t g"})
  void test_noIdProvided(String inputStr) {
    VCF userInput = VCF.parse(inputStr);
    assertParsedInput(true, inputStr, "Y", 987654321, null, "T", "G", "Y-987654321", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"9 123 . a", "10 456 c1 c", "11 789 1g g", "12 987 45 t",
          "y 987654321 rs123 t gXYZ"})
  void test_invalidInputs(String inputStr) {
    assertFalse(VCF.isValid(inputStr));
  }

  private void assertParsedInput(boolean valid, String inputStr, String chr, Integer pos, String id, String ref, String alt,
                           String group, VCF actual) {
    assertEquals(valid, actual.isValid());
    assertEquals(inputStr, actual.getInputStr());
    assertEquals(chr, actual.getChr());
    assertEquals(pos, actual.getPos());
    assertEquals(id, actual.getId());
    assertEquals(ref, actual.getRef());
    assertEquals(alt, actual.getAlt());
    assertEquals(group, actual.groupByChrAndPos());
  }
}
