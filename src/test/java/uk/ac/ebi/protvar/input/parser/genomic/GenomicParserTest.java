package uk.ac.ebi.protvar.input.parser.genomic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.GenomicInput;

import static org.junit.jupiter.api.Assertions.*;

// class includes test for internal genomic input

class GenomicParserTest {

    // Simple valid cases for the 3 formats
    // 1 chr pos
    // 2 chr pos ref
    // 3 chr pos ref alt
    @ParameterizedTest
    @ValueSource(strings = {"1 123", "1    123"})
    // in-between spaces work; spaces at the start/end don't - inputStr is trim before parse
    void test_chrPos(String inputStr) {
        assertTrue(GenomicParser.matchesPattern(inputStr));
        GenomicInput parsedInput = GenomicParser.parse(inputStr);
        assertInput(true, inputStr, "1", 123, null, null, parsedInput);
    }

    @Test
    void test_chrPosRef() {
        String inputStr = "1 123 A";
        assertTrue(GenomicParser.matchesPattern(inputStr));
        GenomicInput parsedInput = GenomicParser.parse(inputStr);
        assertInput(true, inputStr, "1", 123, "A", null, parsedInput);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x 123 a t", "x 123 a/t", "x 123 a>t"})
    void test_chrPosRefAlt(String inputStr) {
        assertTrue(GenomicParser.matchesPattern(inputStr));
        GenomicInput parsedInput = GenomicParser.parse(inputStr);
        assertInput(true, inputStr, "X", 123, "A", "T", parsedInput);
    }

    @Test
    void test_inputMT() {
        String inputStr = "mt 4 g c";
        assertTrue(GenomicParser.matchesPattern(inputStr));
        GenomicInput parsedInput = GenomicParser.parse(inputStr);
        assertInput(true, inputStr, GenomicParser.MT, 4, "G", "C", parsedInput);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3 123", "4 456", "x 789", "Y 987", "22 654"})
    void test_moreChrAndPos(String inputStr) {
        GenomicInput parsedInput = GenomicParser.parse(inputStr);
        String[] tokens = inputStr.split(" ");
        assertInput(true, inputStr, tokens[0].toUpperCase(), Integer.parseInt(tokens[1]),
                null, null, parsedInput);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5 123 A", "6 456 C", "7 789 G", "8 987 T"})
    // will not consider id if 3rd is base
    void test_moreChrPosRef(String inputStr) {
        GenomicInput parsedInput = GenomicParser.parse(inputStr);
        String[] tokens = inputStr.split(" ");
        assertInput(true, inputStr, tokens[0].toUpperCase(), Integer.parseInt(tokens[1]),
                tokens[2], null, parsedInput);
    }


    @ParameterizedTest
    @ValueSource(strings = {"13 0", "14 a", "15 $", "16 a1", "17 b2£", "18 *t"})
    void test_validChrInvalidPosition(String inputStr) {
        GenomicInput parsedInput = GenomicParser.parse(inputStr);
        assertTrue(parsedInput.getErrors().stream().anyMatch(ErrorConstants.INVALID_POS.getErrorMessage()::equalsIgnoreCase));
    }

    @Test
    void testInvalidInputTabs() {
        String input = "21\t25891796\t25891797\tC . . .";
        assertFalse(GenomicParser.matchesPattern(input));
    }

    @Test
    void testInvalidInputDots() {
        String input = "21 25891796 25891797 . . .";
        assertFalse(GenomicParser.matchesPattern(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", // chr 1-10
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", // chr 11-20
            "21", "22", // chr 21-22
            "01", "002", // leading zero in chr number
            "X", "Y", // chr X,Y
            "x", "y", // chr X,Y small case
            "chrM", "mitochondria", "mitochondrion", "MT", "mtDNA", "mit", // chr MT
            "ChrM", "Mitochondria", "Mitochondrion", "mt", "mtdna", "MIT", // chr MT diff cap
            " ChrM", "Mitochondria ", " MT " , // chr MT spaces
            " 1", "2 ", " 3 ", // with left/right spaces
            "chr1", "chrX", "M"
    })
    void test_validChr(String chr) {
        assertTrue(GenomicParser.validChr(chr));
    }

    @ParameterizedTest
    @NullSource
    void test_invalidChr_null(String chr) {
        assertFalse(GenomicParser.validChr(chr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", // empty
            "0", "23", "200", // edge cases
            "Z", // non-XY
            "chr M" // inbetween space
    })
    void test_invalidChr(String chr) {
        assertFalse(GenomicParser.validChr(chr));
    }


    @ParameterizedTest
    @ValueSource(strings = {"1", "1000", " 10 ", "00100"
    })
    void test_validPos(String pos) {
        assertTrue(GenomicParser.validPos(pos));
    }

    @ParameterizedTest
    @NullSource
    void test_validPos_null(String pos) {
        assertFalse(GenomicParser.validPos(pos));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "-1", "0", "abc", "1x"
    })
    void test_validPos_false(String pos) {
        assertFalse(GenomicParser.validPos(pos));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", " A", "A ", " A ", "T", "C", "G", "a", "t", "c", "g"
    })
    void test_validBase(String base) {
        assertTrue(GenomicParser.validBase(base));
    }

    @ParameterizedTest
    @NullSource
    void test_invalidBase_null(String base) {
        assertFalse(GenomicParser.validBase(base));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "AA", "aa", "U", "X", "Y", "1"
    })
    void test_invalidBase(String base) {
        assertFalse(GenomicParser.validBase(base));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1", " 1", " 1 ", "01", "001"
    })
    void test_convertChr_valid_num(String chr) {
        assertEquals("1", GenomicParser.convertChr(chr));
    }

    @ParameterizedTest
    @ValueSource(strings = { "chrM", "mitochondria", "mitochondrion", "MT", "mtDNA", "mit", // chr MT
            "ChrM", "Mitochondria", "Mitochondrion", "mt", "mtdna", "MIT", // chr MT diff cap
            " ChrM", "Mitochondria ", " MT " , // chr MT spaces
    })
    void test_convertChr_valid_MT(String mt) {
        assertEquals("MT", GenomicParser.convertChr(mt));
    }

    @Test
    void test_convertChr_valid_other() {
        assertEquals("X", GenomicParser.convertChr("x"));
        assertEquals("X", GenomicParser.convertChr(" X"));
        assertEquals("X", GenomicParser.convertChr(" X "));
    }

    @Test
    void test_convertPos_valid() {
        assertEquals(1, GenomicParser.convertPosition("1"));
        assertEquals(1000, GenomicParser.convertPosition("1000"));
        assertEquals(10, GenomicParser.convertPosition(" 10 "));
        assertEquals(100, GenomicParser.convertPosition("00100"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "x", "10000000000000000000000"
    })
    void test_convertPos_invalid(String pos) {
        assertEquals(Integer.valueOf(-1), GenomicParser.convertPosition(pos));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1 12345678", "X 2123423423", "Y  234235235", "chrM 23424", "22  23423432", // no ref or alt
            "Y 2424234235345 A", "21    2345   T", "0001  23423525   G", "22 23545643  C", // one base
            "17 7456845 A  t", "10    1012145   T  c", "0001  23423525   G t", "22 23545643  C a"// both bases - ref and alt
    })
    void test_validInternalInput_valid(String input) {
        assertTrue(GenomicParser.matchesPattern(input));
    }

    @ParameterizedTest
    @ValueSource(strings = { "17 7456845 A  t", "10    1012145   T>c", "0001  23423525   G/t", "22 23545643  C/a"// spaces or slash or greater than sign
    })
    void test_validInternalInput_valid_sub_sign(String input) {
        assertTrue(GenomicParser.matchesPattern(input));
    }

    @Test
    void test_parse() {
        String inputStr = "x 123 a t";
        VariantInput input = GenomicParser.parse(inputStr); // from previous VCFTest
        assertInput(true, inputStr, "X", 123, "A", "T", (GenomicInput)input);
    }

    private void assertInput(boolean valid, String inputStr, String chr, Integer pos, String ref, String alt,
                                   GenomicInput actual) {
        assertEquals(valid, actual.isValid());
        assertEquals(inputStr, actual.getInputStr());
        assertEquals(chr, actual.getChromosome());
        assertEquals(pos, actual.getPosition());
        assertEquals(ref, actual.getRefBase());
        assertEquals(alt, actual.getAltBase());
    }
}