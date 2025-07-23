package uk.ac.ebi.protvar.input.parser.hgvs;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.HGVSCodingInput;

class HGVScParserTest {

    @Test
    void test_valid() {
        HGVSCodingInput g = HGVScParser.parse("NM_004006.2:c.234C>G");
        assert "NM_004006.2".equals(g.getRefseqId());
        assert g.getPosition() != null && g.getPosition() == 234;
        assert "C".equals(g.getRefBase());
        assert "G".equals(g.getAltBase());
    }
    @Test
    void test_invalid_prefix() {
        HGVSCodingInput i = HGVScParser.parse("NX_004006.2:c.234C>G");
        assert i.hasError();
    }

    @Test
    void test_valid2() {
        String inputStr = "NM_017547.4:c.1289A>G";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, null, null, null, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info() {
        String inputStr = "NM_017547.4(FOXRED1):c.1289A>Gp.(Asn430Ser)";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info_spaces() {
        String inputStr = "NM_017547.4 (FOXRED1):c.1289A>G p.(Asn430Ser)";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info_spaces_right() {
        String inputStr = "NM_017547.4(FOXRED1):c.1289A>G p.(Asn430Ser)";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info_spaces_left() {
        String inputStr = "NM_017547.4 (FOXRED1):c.1289A>Gp.(Asn430Ser)";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_only() {
        String inputStr = "NM_017547.4(FOXRED1):c.1289A>G";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", null, null, null, parsedInput);
    }

    @Test
    void test_valid_with_protein_info_only() {
        String inputStr = "NM_017547.4:c.1289A>Gp.(Asn430Ser)";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_bracket_before_prot_desc() {
        String inputStr = "NM_017547.4:c.1289A>G(p.Asn430Ser)";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_bracket_before_prot_desc_with_space() {
        String inputStr = "NM_017547.4:c.1289A>G (p.Asn430Ser)";
        HGVSCodingInput parsedInput = HGVScParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, "Asn", "Ser", 430, parsedInput);
    }

    private void assertInput(boolean valid, String inputStr, String refseqId, int pos, String ref, String alt,
                             String gene, String protRef, String protAlt, Integer protPos, HGVSCodingInput actual) {
        assert valid == actual.isValid();
        assert inputStr.equals(actual.getInputStr());
        assert refseqId.equals(actual.getRefseqId());
        assert actual.getPosition() != null && actual.getPosition() == pos;
        assert ref.equals(actual.getRefBase());
        assert alt.equals(actual.getAltBase());
        // optional fields
        assert gene == null || gene.equals(actual.getGeneSymbol());
        assert protRef == null || protRef.equals(actual.getRefAA());
        assert protAlt == null || protAlt.equals(actual.getAltAA());
        assert protPos == null || protPos.equals(actual.getAaPos());
    }
}