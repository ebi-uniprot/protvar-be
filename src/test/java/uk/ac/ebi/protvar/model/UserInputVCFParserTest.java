package uk.ac.ebi.protvar.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.parser.GenericParser;
import uk.ac.ebi.protvar.parser.VCFParser;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.ebi.protvar.parser.GenericParser.DB_MT_CHROMOSOME;

class UserInputVCFParserTest {
  @ParameterizedTest
  @NullAndEmptySource
  void emptyLineIsInvalidObject(String input) {
    UserInput userInput = VCFParser.parse(input);
    assertEquals(UserInput.invalidObject(input, UserInput.Type.VCF), userInput);
  }

  @Test
  void minimumInput() {
    var input = "x 123 a/t";
    UserInput userInput = VCFParser.parse(input);
    assertInput(true, input, "X", 123L, Constants.NA, "A", "T", "X-123", userInput);
  }

  @Test
  void inputWithoutId() {
    var input = "mt 4 g c";
    UserInput userInput = VCFParser.parse(input);
    assertInput(true, input, DB_MT_CHROMOSOME, 4L, Constants.NA, "G", "C", DB_MT_CHROMOSOME + "-4", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"y 987654321 rs123 t g", "y 987654321 rs123 t g description;",
    "y 987654321 rs123 t g 100 PASS AA=T|||;AC=1;AF=0"})
  void inputWithId(String input) {
    UserInput userInput = VCFParser.parse(input);
    assertInput(true, input, "Y", 987654321L, "rs123", "T", "G", "Y-987654321", userInput);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2", "X", "y", "21"})
  void onlyChromProvided(String input) {
    UserInput userInput = VCFParser.parse(input);
    assertInput(false, input, input.toUpperCase(), -1L, Constants.NA, Constants.NA, Constants.NA, input.toUpperCase() + "--1", userInput);
    assertEquals("Position should be a number |Invalid reference |Invalid alternative ", userInput.getInvalidReason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3 123", "4 456", "x 789", "Y 987", "22 654"})
  void chromAndPositionProvided(String input) {
    UserInput userInput = VCFParser.parse(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), Constants.NA, Constants.NA, Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid reference |Invalid alternative ", userInput.getInvalidReason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"5 123 1", "6 456 .", "7 789 rc12", "8 987 n/a"})
  void threeInputs_3rdWillConsiderId(String input) {
    UserInput userInput = VCFParser.parse(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), tokens[2], Constants.NA, Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid reference |Invalid alternative ", userInput.getInvalidReason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"5 123 A", "6 456 C", "7 789 G", "8 987 T"})
  void threeInputs_3rdWillNotConsiderIdIfItIsAllele(String input) {
    UserInput userInput = VCFParser.parse(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), Constants.NA, tokens[2], Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid alternative ", userInput.getInvalidReason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"9 123 . a", "10 456 c1 c", "11 789 1g g", "12 987 45 t"})
  void chromPositionIdRefProvided(String input) {
    UserInput userInput = VCFParser.parse(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0].toUpperCase(), Long.parseLong(tokens[1]), tokens[2], tokens[3].toUpperCase(), Constants.NA,
      tokens[0].toUpperCase() + "-" + tokens[1], userInput);
    assertEquals("Invalid alternative ", userInput.getInvalidReason());
  }

  @ParameterizedTest
  @CsvSource({"23 1,23,1", "z 2,z,2", "mat 3,mat,3"})
  void invalidChromValidPosition(String input, String chr, Long pos) {
    UserInput userInput = VCFParser.parse(input);
    assertInput(false, input, Constants.NA, pos, Constants.NA, Constants.NA, Constants.NA, "N/A-" + pos, userInput);
    assertEquals("Invalid chromosome "+chr+"|Invalid reference |Invalid alternative ", userInput.getInvalidReason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"13 0", "14 a", "15 $", "16 a1", "17 b2£", "18 *t"})
  void validChromInvalidPosition(String input) {
    UserInput userInput = VCFParser.parse(input);
    String[] tokens = input.split(" ");
    assertInput(false, input, tokens[0], -1L, Constants.NA, Constants.NA, Constants.NA, tokens[0]+"--1", userInput);
    assertEquals("Position should be a number "+tokens[1]+"|Invalid reference |Invalid alternative ", userInput.getInvalidReason());
  }

  @Test
  void testInvalidInputTabs() {
    String input = "21\t25891796\t25891797\tC . . .";
    UserInput actual = UserInput.getInput(input);
    assertInput(false, input, "21", 25891796L, "25891797", "C", Constants.NA, "21-25891796", actual);
  }

  @Test
  void testInvalidInputDots() {
    String input = "21 25891796 25891797 . . .";
    UserInput actual = UserInput.getInput(input);
    assertInput(false, input, "21", 25891796L, "25891797", Constants.NA, Constants.NA, "21-25891796", actual);
  }

  private void assertInput(boolean valid, String input, String chr, Long pos, String id, String ref, String alt,
                           String group, UserInput actual) {
    assertEquals(valid, actual.isValid());
    assertEquals(input, actual.getFormattedInputString());
    assertEquals(chr, actual.getChromosome());
    assertEquals(pos, actual.getStart());
    assertEquals(id, actual.getId());
    assertEquals(ref, actual.getRef());
    assertEquals(alt, actual.getAlt());
    assertEquals(group, actual.getGroupBy());
  }

  @Nested
  class isIdTest {
    @Test
    void emptyListWillSayCannotGetUserId() {
      assertFalse(VCFParser.isIdExist(new LinkedList<>()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "C", "G", "T"})
    void alleleCannotBeUserIds(String allele) {
      assertFalse(VCFParser.isIdExist(new LinkedList<>(List.of(allele))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "c", "g", "t"})
    void smallCaseAlleleCannotBeUserIds(String allele) {
      assertFalse(VCFParser.isIdExist(new LinkedList<>(List.of(allele))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A/C", "C/G", "G/T", "T/A", "c/a", "g/c", "t/g", "a/t"})
    void combinationAlleleCannotBeUserIds(String allele) {
      assertFalse(VCFParser.isIdExist(new LinkedList<>(List.of(allele))));
    }
  }
}
