package uk.ac.ebi.protvar.input.parser.genomic;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.input.GenomicInput;

import static org.junit.jupiter.api.Assertions.*;

class VCFParserTest {

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
    GenomicInput userInput = VCFParser.parse(inputStr);
    assertParsedInput(true, inputStr, "Y", 987654321, "rs123", "T", "G", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"y 987654321 . t g"})
  void test_noIdProvided(String inputStr) {
    GenomicInput userInput = VCFParser.parse(inputStr);
    assertParsedInput(true, inputStr, "Y", 987654321, null, "T", "G", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"9 ", // 1 col
          "10 456 ", // 2 cols
          "11 789 1g", // 3 cols
          "12 987 45 t", // 4 cols
          })
  void test_matchesPatternFalse(String inputStr) {
    assertFalse(VCFParser.matchesPattern(inputStr));
  }

  private void assertParsedInput(boolean valid, String inputStr, String chr, Integer pos, String id, String ref, String alt,
                           GenomicInput actual) {
    assertEquals(valid, actual.isValid());
    assertEquals(inputStr, actual.getInputStr());
    assertEquals(chr, actual.getChr());
    assertEquals(pos, actual.getPos());
    assertEquals(id, actual.getId());
    assertEquals(ref, actual.getRef());
    assertEquals(alt, actual.getAlt());
  }
}
