package uk.ac.ebi.protvar.input.format.coding;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.parser.hgvs.HGVScInputParser;

import static org.junit.jupiter.api.Assertions.*;

class HGVScTest {

    @Test
    void test_valid() {
        HGVSc g = HGVScInputParser.parse("NM_004006.2:c.234C>G");
        assertEquals(g.getRsAcc(), "NM_004006.2");
        assertEquals(g.getPos(), 234);
        assertEquals(g.getRef(), "C");
        assertEquals(g.getAlt(), "G");
    }
    @Test
    void test_invalid_prefix() {
        HGVSc g = HGVScInputParser.parse("NX_004006.2:c.234C>G");
        assertTrue(g.hasError());
    }

    @Test
    void test_valid2() {
        String inputStr = "NM_017547.4:c.1289A>G";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, null, null, null, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info() {
        String inputStr = "NM_017547.4(FOXRED1):c.1289A>Gp.(Asn430Ser)";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info_spaces() {
        String inputStr = "NM_017547.4 (FOXRED1):c.1289A>G p.(Asn430Ser)";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info_spaces_right() {
        String inputStr = "NM_017547.4(FOXRED1):c.1289A>G p.(Asn430Ser)";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_and_protein_info_spaces_left() {
        String inputStr = "NM_017547.4 (FOXRED1):c.1289A>Gp.(Asn430Ser)";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_gene_only() {
        String inputStr = "NM_017547.4(FOXRED1):c.1289A>G";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", "FOXRED1", null, null, null, parsedInput);
    }

    @Test
    void test_valid_with_protein_info_only() {
        String inputStr = "NM_017547.4:c.1289A>Gp.(Asn430Ser)";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_bracket_before_prot_desc() {
        String inputStr = "NM_017547.4:c.1289A>G(p.Asn430Ser)";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, "Asn", "Ser", 430, parsedInput);
    }

    @Test
    void test_valid_with_bracket_before_prot_desc_with_space() {
        String inputStr = "NM_017547.4:c.1289A>G (p.Asn430Ser)";
        HGVSc parsedInput = HGVScInputParser.parse(inputStr);
        assertInput(true, inputStr, "NM_017547.4", 1289 , "A", "G", null, "Asn", "Ser", 430, parsedInput);
    }

    private void assertInput(boolean valid, String inputStr, String rsAcc, Integer pos, String ref, String alt,
                             String gene, String protRef, String protAlt, Integer protPos, HGVSc actual) {
        assertEquals(valid, actual.isValid());
        assertEquals(inputStr, actual.getInputStr());
        assertEquals(rsAcc, actual.getRsAcc());
        assertEquals(pos, actual.getPos());
        assertEquals(ref, actual.getRef());
        assertEquals(alt, actual.getAlt());
        // optional fields
        assertEquals(gene, actual.getGene());
        assertEquals(protRef, actual.getProtRef());
        assertEquals(protAlt, actual.getProtAlt());
        assertEquals(protPos, actual.getProtPos());
    }
}