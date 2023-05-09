package uk.ac.ebi.protvar.input;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VCFInputTest {

  @Test
  void minimumInput() {
    var input = "x 123 a/t";
    VCFInput userInput = new VCFInput(input);
    assertInput(true, input, "X", 123L, Constants.NA, "A", "T", "X-123", userInput);
  }

  @Test
  void inputWithoutId() {
    var input = "mt 4 g c";
    VCFInput userInput = new VCFInput(input);
    assertInput(true, input, VCFInput.DB_MT_CHROMOSOME, 4L, Constants.NA, "G", "C", VCFInput.DB_MT_CHROMOSOME + "-4", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"y 987654321 rs123 t g", "y 987654321 rs123 t g description;",
    "y 987654321 rs123 t g 100 PASS AA=T|||;AC=1;AF=0"})
  void inputWithId(String input) {
    VCFInput userInput = new VCFInput(input);
    assertInput(true, input, "Y", 987654321L, "rs123", "T", "G", "Y-987654321", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2", "X", "y", "21"})
  void onlyChromProvided(String input) {
    VCFInput userInput = new VCFInput(input);
    assertInput(false, input, input.toUpperCase(), -1L, Constants.NA, Constants.NA, Constants.NA, input.toUpperCase() + "--1", userInput);
    assertEquals("Position should be a number |Invalid reference |Invalid alternative ", String.join("|", userInput.getErrors()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"3 123", "4 456", "x 789", "Y 987", "22 654"})
  void chromAndPositionProvided(String input) {
    VCFInput userInput = new VCFInput(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), Constants.NA, Constants.NA, Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid reference |Invalid alternative ", String.join("|", userInput.getErrors()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"5 123 1", "6 456 .", "7 789 rc12", "8 987 n/a"})
  void threeInputs_3rdWillConsiderId(String input) {
    VCFInput userInput = new VCFInput(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), tokens[2], Constants.NA, Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid reference |Invalid alternative ", String.join("|", userInput.getErrors()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"5 123 A", "6 456 C", "7 789 G", "8 987 T"})
  void threeInputs_3rdWillNotConsiderIdIfItIsAllele(String input) {
    VCFInput userInput = new VCFInput(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), Constants.NA, tokens[2], Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid alternative ", String.join("|", userInput.getErrors()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"9 123 . a", "10 456 c1 c", "11 789 1g g", "12 987 45 t"})
  void chromPositionIdRefProvided(String input) {
    VCFInput userInput = new VCFInput(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), tokens[2], tokens[3].toUpperCase(), Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid alternative ", String.join("|", userInput.getErrors()));
  }

  @ParameterizedTest
  @CsvSource({"23 1,23,1", "z 2,z,2", "mat 3,mat,3"})
  void invalidChromValidPosition(String input, String chr, Long pos) {
    VCFInput userInput = new VCFInput(input);
    assertInput(false, input, Constants.NA, pos, Constants.NA, Constants.NA, Constants.NA, "N/A-" + pos, userInput);
    assertEquals("Invalid chromosome "+chr+"|Invalid reference |Invalid alternative ", String.join("|", userInput.getErrors()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"13 0", "14 a", "15 $", "16 a1", "17 b2£", "18 *t"})
  void validChromInvalidPosition(String input) {
    VCFInput userInput = new VCFInput(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0], -1L, Constants.NA, Constants.NA, Constants.NA, tokens[0]+"--1", userInput);
    assertEquals("Position should be a number "+tokens[1]+"|Invalid reference |Invalid alternative ", String.join("|", userInput.getErrors()));
  }

  @Test
  void testInvalidInputTabs() {
    String input = "21\t25891796\t25891797\tC . . .";
    VCFInput actual = new VCFInput(input);
    assertInput(false, input, "21", 25891796L, "25891797", "C", Constants.NA, "21-25891796", actual);
  }

  @Test
  void testInvalidInputDots() {
    String input = "21 25891796 25891797 . . .";
    VCFInput actual = new VCFInput(input);
    assertInput(false, input, "21", 25891796L, "25891797", Constants.NA, Constants.NA, "21-25891796", actual);
  }

  private void assertInput(boolean valid, String input, String chr, Long pos, String id, String ref, String alt,
                           String group, VCFInput actual) {
    assertEquals(valid, actual.isValid());
    assertEquals(chr, actual.getChr());
    assertEquals(pos, actual.getPos());
    assertEquals(id, actual.getId());
    assertEquals(ref, actual.getRef());
    assertEquals(alt, actual.getAlt());
    assertEquals(group, actual.groupByChrAndPos());
  }

  @Nested
  class isIdTest {
    @Test
    void emptyListWillSayCannotGetUserId() {
      assertFalse(VCFInput.isIdExist(new LinkedList<>()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "C", "G", "T"})
    void alleleCannotBeUserIds(String allele) {
      assertFalse(VCFInput.isIdExist(new LinkedList<>(List.of(allele))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "c", "g", "t"})
    void smallCaseAlleleCannotBeUserIds(String allele) {
      assertFalse(VCFInput.isIdExist(new LinkedList<>(List.of(allele))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A/C", "C/G", "G/T", "T/A", "c/a", "g/c", "t/g", "a/t"})
    void combinationAlleleCannotBeUserIds(String allele) {
      assertFalse(VCFInput.isIdExist(new LinkedList<>(List.of(allele))));
    }
  }
}
