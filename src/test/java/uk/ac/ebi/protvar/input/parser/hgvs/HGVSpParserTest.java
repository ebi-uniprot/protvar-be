package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ProteinInput;

class HGVSpParserTest {

    @Test
    void testValid3LetterAA() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Arg490Ser");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() != null && userInput.getPosition() == 490;
        assert "Arg".equals(userInput.getRefAA());
        assert "Ser".equals(userInput.getAltAA());
    }

    @Test
    void testValid1LetterAA() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.R490S");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() != null && userInput.getPosition() == 490;
        assert "R".equals(userInput.getRefAA());
        assert "S".equals(userInput.getAltAA());
    }

    @Test
    void testValid3LetterAA_Ter() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Trp87Ter");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() != null && userInput.getPosition() == 87;
        assert "Trp".equals(userInput.getRefAA());
        assert "Ter".equals(userInput.getAltAA());
    }

    @Test
    void testValid3LetterAA_StopCodon() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Trp78*");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() != null && userInput.getPosition() == 78;
        assert "Trp".equals(userInput.getRefAA());
        assert "*".equals(userInput.getAltAA());
    }

    @Test
    void testValid1LetterAA_StopCodon() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.W87*");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() != null && userInput.getPosition() == 87;
        assert "W".equals(userInput.getRefAA());
        assert "*".equals(userInput.getAltAA());
    }

    @Test
    void testInvalid3and1LetterAA() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Arg87S");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() == null;
        assert userInput.getRefAA() == null;
        assert userInput.getAltAA() == null;
        assert userInput.hasError();
    }

    @Test
    void testValidEqualForAlt() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.Trp78=");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() != null && userInput.getPosition() == 78;
        assert "Trp".equals(userInput.getRefAA());
        assert "Trp".equals(userInput.getAltAA());
    }

    @Test
    void testValidBrackets1() {
        ProteinInput userInput = HGVSpParser.parse("NP_003997.1:p.(Trp78*)");
        assert "NP_003997.1".equals(userInput.getRefseqId());
        assert userInput.getPosition() != null && userInput.getPosition() == 78;
        assert "Trp".equals(userInput.getRefAA());
        assert "*".equals(userInput.getAltAA());
    }
}