package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ProteinInput;

class HGVSpParserTest {

    @Test
    void testValid3LetterAA() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Arg490Ser");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() != null && userInput.getPos() == 490;
        assert "Arg".equals(userInput.getRef());
        assert "Ser".equals(userInput.getAlt());
    }

    @Test
    void testValid1LetterAA() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.R490S");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() != null && userInput.getPos() == 490;
        assert "R".equals(userInput.getRef());
        assert "S".equals(userInput.getAlt());
    }

    @Test
    void testValid3LetterAA_Ter() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Trp87Ter");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() != null && userInput.getPos() == 87;
        assert "Trp".equals(userInput.getRef());
        assert "Ter".equals(userInput.getAlt());
    }

    @Test
    void testValid3LetterAA_StopCodon() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Trp78*");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() != null && userInput.getPos() == 78;
        assert "Trp".equals(userInput.getRef());
        assert "*".equals(userInput.getAlt());
    }

    @Test
    void testValid1LetterAA_StopCodon() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.W87*");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() != null && userInput.getPos() == 87;
        assert "W".equals(userInput.getRef());
        assert "*".equals(userInput.getAlt());
    }

    @Test
    void testInvalid3and1LetterAA() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Arg87S");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() == null;
        assert userInput.getRef() == null;
        assert userInput.getAlt() == null;
        assert userInput.hasError();
    }

    @Test
    void testValidEqualForAlt() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Trp78=");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() != null && userInput.getPos() == 78;
        assert "Trp".equals(userInput.getRef());
        assert "Trp".equals(userInput.getAlt());
    }

    @Test
    void testValidBrackets1() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.(Trp78*)");
        assert "NP_003997.1".equals(userInput.getRsAcc());
        assert userInput.getPos() != null && userInput.getPos() == 78;
        assert "Trp".equals(userInput.getRef());
        assert "*".equals(userInput.getAlt());
    }
}