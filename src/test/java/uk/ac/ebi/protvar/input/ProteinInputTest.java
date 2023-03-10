package uk.ac.ebi.protvar.input;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.utils.AminoAcid;

import java.util.regex.Pattern;

public class ProteinInputTest {

    @Test
    void testProteinAccessionsList() {
        assert(!ProteinInput.PROTEIN_ACCESSIONS.isEmpty());
        assert(ProteinInput.PROTEIN_ACCESSIONS.contains("Q4ZIN3"));
    }

    @Test
    void testPatternAA1() {
        for (String c : AminoAcid.VALID_AA1) {
            assert (Pattern.matches(ProteinInput.AA1, c));
        }
    }

    @Test
    void testPatternAA3() {
        for (String c : AminoAcid.VALID_AA3) {
            assert(Pattern.matches(ProteinInput.AA3, c));
        }
        assert(Pattern.matches(ProteinInput.AA3, "VAL"));
        assert(!Pattern.matches(ProteinInput.AA3, "XXX"));
    }

    @Test // ACC X 999 Y
    void testUserInputAccXPosY_space() {
        assertUserInput("Q4ZIN3 S 558 R");
    }
    @Test // ACC/X/999/Y
    void testUserInputAccXPosY_slash() {
        assertUserInput("Q4ZIN3/S/558/R");
    }
    @Test // ACC X/999/Y
    void testUserInputAccXPosY_spaceAndSlash() {
        assertUserInput("Q4ZIN3 S/558/R");
    }

    @Test
    void testUserInputAccPosXY_space() {
        assertUserInput("Q4ZIN3 558 S R");
    }

    @Test
    void testUserInputAccPosXY_spaces() {
        assertUserInput("Q4ZIN3 558    S     R");
    }

    @Test
    void testUserInputAccPosXY_spaceAndSlash() {
        assertUserInput("Q4ZIN3 558 S/R");
    }

    @Test
    void testUserInputAccPosXxxYyy_spaceAndSlash() {
        assertUserInput("Q4ZIN3 558 Ser/Arg");
    }

    @Test
    void testUserInputAccPosXxxYyy_space() {
        assertUserInput("Q4ZIN3 558 Ser Arg");
    }

    @Test
    void testUserInputAccXPosY_spaceNoSpace() {
        assertUserInput("Q4ZIN3 S558R");
    }

    @Test
    void testUserInputAccXxxPosYyy() {
        assertUserInput("Q4ZIN3 Ser558Arg");
    }

    @Test
    void testUserInputAccPdotXxxPosYyy() {
        assertUserInput("Q4ZIN3 p.Ser558Arg");
    }

    @Test
    void testUserInputAccPosYyy() {
        ProteinInput userInput = new ProteinInput("Q4ZIN3/558/Arg");
        assert(userInput.getAcc().equals("Q4ZIN3"));
        assert(userInput.getPos() == 558);
        assert(userInput.getRef() == null);
        assert(userInput.getAlt().equals("R"));
    }

    private void assertUserInput(String input) {
        ProteinInput userInput = new ProteinInput(input);
        assert(userInput.getAcc().equals("Q4ZIN3"));
        assert(userInput.getPos() == 558);
        assert(userInput.getRef().equals("S"));
        assert(userInput.getAlt().equals("R"));
    }

}
