package uk.ac.ebi.protvar.input.type;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.protein.ProteinInputParser;
import uk.ac.ebi.protvar.types.AminoAcid;

import java.util.regex.Pattern;

public class ProteinInputTest {

    @Test
    void testOneLetterAA() {
        for (String c : AminoAcid.VALID_AA1) {
            assert (Pattern.matches(ProteinInputParser.AMINO_ACID_REF1, c));
        }
    }

    @Test
    void testThreeLetterAA() {
        for (String c : AminoAcid.VALID_AA3) {
            assert(Pattern.matches(ProteinInputParser.AMINO_ACID_REF3, c));
        }
        assert(Pattern.matches(ProteinInputParser.AMINO_ACID_REF3, "VAL"));
        assert(!Pattern.matches(ProteinInputParser.AMINO_ACID_REF3, "XXX"));
    }

    @Test // ACC 999
    void testAccessionAndPosOnly() {
        assertInput("Q4ZIN3 558", "Q4ZIN3", 558, null, null);
    }

    @Test // ACC 999 X Y
    void testAccPosXY() {
        assertInput("Q4ZIN3 558 S R", "Q4ZIN3", 558, "S",  "R");
    }

    @Test //// ACC 999    X     Y
    void testAccPosXY_spaces() {
        assertInput("Q4ZIN3 558    S     R", "Q4ZIN3", 558, "S", "R");
    }

    @Test // ACC 999 X/Y
    void testAccPosXslashY() {
        assertInput("Q4ZIN3 558 S/R", "Q4ZIN3", 558, "S", "R");
    }

    @Test // ACC 999 XXX/YYY
    void testAccPosXXXslashYYYY() {
        assertInput("Q4ZIN3 558 Ser/Arg", "Q4ZIN3", 558, "S", "R");
    }

    @Test // ACC 999 XXX YYY
    void testAccPosXXXYYYY() {
        assertInput("Q4ZIN3 558 Ser Arg", "Q4ZIN3", 558, "S", "R");
    }

    @Test // ACC X999Y
    void testAccXPosY() {
        assertInput("Q4ZIN3 S558R", "Q4ZIN3", 558, "S", "R");
    }

    @Test // ACC XXX999YYY
    void testAccXXXPosYYY() {
        assertInput("Q4ZIN3 Ser558Arg", "Q4ZIN3", 558, "S", "R");
    }

    @Test // ACC p.XXX999YYY
    void testAccPdotXXXPosYYY() {
        assertInput("Q4ZIN3 p.Ser558Arg", "Q4ZIN3", 558, "S", "R");
    }

    private void assertInput(String input, String acc, Integer pos, String ref, String alt) {
        ProteinInput userInput = ProteinInputParser.parse(input);
        Assertions.assertEquals(acc, userInput.getAcc());
        Assertions.assertEquals(pos, userInput.getPos());
        Assertions.assertEquals(ref, userInput.getRef());
        Assertions.assertEquals(alt, userInput.getAlt());
    }

}
