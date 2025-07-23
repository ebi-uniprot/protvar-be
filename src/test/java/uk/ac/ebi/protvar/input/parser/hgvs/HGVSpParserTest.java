package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ProteinInput;

class HGVSpParserTest {

    @Test
    void testValid3LetterAA() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.Arg490Ser");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() != null && input.getPosition() == 490;
        assert "Arg".equals(input.getRefAA());
        assert "Ser".equals(input.getAltAA());
    }

    @Test
    void testValid1LetterAA() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.R490S");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() != null && input.getPosition() == 490;
        assert "R".equals(input.getRefAA());
        assert "S".equals(input.getAltAA());
    }

    @Test
    void testValid3LetterAA_Ter() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.Trp87Ter");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() != null && input.getPosition() == 87;
        assert "Trp".equals(input.getRefAA());
        assert "Ter".equals(input.getAltAA());
    }

    @Test
    void testValid3LetterAA_StopCodon() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.Trp78*");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() != null && input.getPosition() == 78;
        assert "Trp".equals(input.getRefAA());
        assert "*".equals(input.getAltAA());
    }

    @Test
    void testValid1LetterAA_StopCodon() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.W87*");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() != null && input.getPosition() == 87;
        assert "W".equals(input.getRefAA());
        assert "*".equals(input.getAltAA());
    }

    @Test
    void testInvalid3and1LetterAA() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.Arg87S");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() == null;
        assert input.getRefAA() == null;
        assert input.getAltAA() == null;
        assert input.hasError();
    }

    @Test
    void testValidEqualForAlt() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.Trp78=");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() != null && input.getPosition() == 78;
        assert "Trp".equals(input.getRefAA());
        assert "Trp".equals(input.getAltAA());
    }

    @Test
    void testValidBrackets1() {
        ProteinInput input = HGVSpParser.parse("NP_003997.1:p.(Trp78*)");
        assert "NP_003997.1".equals(input.getRefseqId());
        assert input.getPosition() != null && input.getPosition() == 78;
        assert "Trp".equals(input.getRefAA());
        assert "*".equals(input.getAltAA());
    }
}