package uk.ac.ebi.protvar.input.format.protein;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HGVSpTest {

    @Test
    void testValid3LetterAA() {
        HGVSp userInput = HGVSp.parse("NP_003997.1:p.Arg490Ser");
        assert(userInput.getRsAcc().equals("NP_003997.1"));
        assert(userInput.getPos() == 490);
        assert(userInput.getRef().equals("Arg"));
        assert(userInput.getAlt().equals("Ser"));
    }

    @Test
    void testValid1LetterAA() {
        HGVSp userInput = HGVSp.parse("NP_003997.1:p.R490S");
        assert(userInput.getRsAcc().equals("NP_003997.1"));
        assert(userInput.getPos() == 490);
        assert(userInput.getRef().equals("R"));
        assert(userInput.getAlt().equals("S"));
    }

    @Test
    void testValid3LetterAA_Ter() {
        HGVSp userInput = HGVSp.parse("NP_003997.1:p.Trp87Ter");
        assert(userInput.getRsAcc().equals("NP_003997.1"));
        assert(userInput.getPos() == 87);
        assert(userInput.getRef().equals("Trp"));
        assert(userInput.getAlt().equals("Ter"));
    }

    @Test
    void testValid3LetterAA_StopCodon() {
        HGVSp userInput = HGVSp.parse("NP_003997.1:p.Trp78*");
        assert(userInput.getRsAcc().equals("NP_003997.1"));
        assert(userInput.getPos() == 78);
        assert(userInput.getRef().equals("Trp"));
        assert(userInput.getAlt().equals("*"));
    }

    @Test
    void testValid1LetterAA_StopCodon() {
        HGVSp userInput = HGVSp.parse("NP_003997.1:p.W87*");
        assert(userInput.getRsAcc().equals("NP_003997.1"));
        assert(userInput.getPos() == 87);
        assert(userInput.getRef().equals("W"));
        assert(userInput.getAlt().equals("*"));
    }

    @Test
    void testInvalid3and1LetterAA() {
        HGVSp userInput = HGVSp.parse("NP_003997.1:p.Arg87S");

        assertEquals(userInput.getRsAcc(), "NP_003997.1");
        assert(userInput.getPos() == null);
        assert(userInput.getRef() == null);
        assert(userInput.getAlt() == null);
        assert(userInput.hasError() == true);
    }
}