package uk.ac.ebi.protvar.input.type;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.utils.Constants;

import static org.junit.jupiter.api.Assertions.*;

// class includes test for custom genomic input

class GenomicInputTest {

    @ParameterizedTest
    @ValueSource(strings = {"x 123 a t", "x 123 a/t", "x 123 a>t"})
    void test_inputWithDiffSubstitutionSigns(String inputStr) {
        assertTrue(GenomicInput.isValid(inputStr));
        GenomicInput parsedInput = GenomicInput.parse(inputStr);
        assertInput(true, inputStr, "X", 123, null, "A", "T", parsedInput);
    }

    @Test
    void test_inputMT() {
        String inputStr = "mt 4 g c";
        assertTrue(GenomicInput.isValid(inputStr));
        GenomicInput parsedInput = GenomicInput.parse(inputStr);
        assertInput(true, inputStr, GenomicInput.MT, 4, null, "G", "C", parsedInput);
    }

    @Test
    @Disabled // this check is now done further down in the logic chain
    void test_inputSameRefAlt() {
        String inputStr = "1 1234 A A";
        assertTrue(GenomicInput.isValid(inputStr));
        GenomicInput parsedInput = GenomicInput.parse(inputStr);
        assertInput(true, inputStr, "1", 1234, Constants.NA, "A", "A", parsedInput);
        assertTrue(!parsedInput.getMessages().isEmpty());
        assertTrue(parsedInput.getMessages().get(0).getType() == Message.MessageType.WARN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3 123", "4 456", "x 789", "Y 987", "22 654"})
    void test_onlyChrAndPos(String inputStr) {
        GenomicInput parsedInput = GenomicInput.parse(inputStr);
        String[] tokens = inputStr.split(" ");
        assertInput(true, inputStr, tokens[0].toUpperCase(), Integer.parseInt(tokens[1]),
                null, null, null, parsedInput);
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {"5 123 1", "6 456 .", "7 789 rc12", "8 987 n/a"})
    // will consider 3rd id
    void test_chrPosAndId(String inputStr) {
        GenomicInput parsedInput = GenomicInput.parse(inputStr);
        String[] tokens = inputStr.split(" ");
        assertInput(true, inputStr, tokens[0].toUpperCase(), Integer.parseInt(tokens[1]),
                tokens[2], null, null, parsedInput);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5 123 A", "6 456 C", "7 789 G", "8 987 T"})
    // will not consider id if 3rd is base
    void test_chrPosAndBase(String inputStr) {
        GenomicInput parsedInput = GenomicInput.parse(inputStr);
        String[] tokens = inputStr.split(" ");
        assertInput(true, inputStr, tokens[0].toUpperCase(), Integer.parseInt(tokens[1]),
                null, tokens[2], null, parsedInput);
    }


    @ParameterizedTest
    @CsvSource({"23 1,23,1", "z 2,z,2", "mat 3,mat,3"})
    void test_invalidChrValidPosition(String inputStr, String chr, Integer pos) {
        assertFalse(GenomicInput.isValid(inputStr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"13 0", "14 a", "15 $", "16 a1", "17 b2£", "18 *t"})
    void test_validChrInvalidPosition(String inputStr) {
        assertFalse(GenomicInput.isValid(inputStr));
    }

    @Test
    void testInvalidInputTabs() {
        String input = "21\t25891796\t25891797\tC . . .";
        assertFalse(GenomicInput.isValid(input));
    }

    @Test
    void testInvalidInputDots() {
        String input = "21 25891796 25891797 . . .";
        assertFalse(GenomicInput.isValid(input));
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
            " 1", "2 ", " 3 " // with left/right spaces
    })
    void test_validChr(String chr) {
        assertTrue(GenomicInput.validChr(chr));
    }

    @ParameterizedTest
    @NullSource
    void test_invalidChr_null(String chr) {
        assertFalse(GenomicInput.validChr(chr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", // empty
            "0", "23", "200", // edge cases
            "M", "Z", // non-XY
            "chr M" // inbetween space
    })
    void test_invalidChr(String chr) {
        assertFalse(GenomicInput.validChr(chr));
    }


    @ParameterizedTest
    @ValueSource(strings = {"1", "1000", " 10 ", "00100"
    })
    void test_validPos(String pos) {
        assertTrue(GenomicInput.validPos(pos));
    }

    @ParameterizedTest
    @NullSource
    void test_validPos_null(String pos) {
        assertFalse(GenomicInput.validPos(pos));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "-1", "0", "abc", "1x"
    })
    void test_validPos_false(String pos) {
        assertFalse(GenomicInput.validPos(pos));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", " A", "A ", " A ", "T", "C", "G", "a", "t", "c", "g"
    })
    void test_validBase(String base) {
        assertTrue(GenomicInput.validBase(base));
    }

    @ParameterizedTest
    @NullSource
    void test_invalidBase_null(String base) {
        assertFalse(GenomicInput.validBase(base));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "AA", "aa", "U", "X", "Y", "1"
    })
    void test_invalidBase(String base) {
        assertFalse(GenomicInput.validBase(base));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1", " 1", " 1 ", "01", "001"
    })
    void test_convertChr_valid_num(String chr) {
        assertEquals("1", GenomicInput.convertChromosome(chr));
    }

    @ParameterizedTest
    @ValueSource(strings = { "chrM", "mitochondria", "mitochondrion", "MT", "mtDNA", "mit", // chr MT
            "ChrM", "Mitochondria", "Mitochondrion", "mt", "mtdna", "MIT", // chr MT diff cap
            " ChrM", "Mitochondria ", " MT " , // chr MT spaces
    })
    void test_convertChr_valid_MT(String mt) {
        assertEquals("MT", GenomicInput.convertChromosome(mt));
    }

    @Test
    void test_convertChr_valid_other() {
        assertEquals("X", GenomicInput.convertChromosome("x"));
        assertEquals("X", GenomicInput.convertChromosome(" X"));
        assertEquals("X", GenomicInput.convertChromosome(" X "));
    }

    @Test
    void test_convertPos_valid() {
        assertEquals(1, GenomicInput.convertPosition("1"));
        assertEquals(1000, GenomicInput.convertPosition("1000"));
        assertEquals(10, GenomicInput.convertPosition(" 10 "));
        assertEquals(100, GenomicInput.convertPosition("00100"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "x", "10000000000000000000000"
    })
    void test_convertPos_invalid(String pos) {
        assertEquals(Integer.valueOf(-1), GenomicInput.convertPosition(pos));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1 12345678", "X 2123423423", "  Y  234235235  ", "chrM 23424", "22  23423432", // no ref or alt
            "Y 2424234235345 A", "21    2345   T", "0001  23423525   G", "22 23545643  C  ", // one base
            "17 7456845 A  t", "10    1012145   T  c", "0001  23423525   G t", "22 23545643  C a "// both bases - ref and alt
    })
    void test_validCustomInput_valid(String input) {
        assertTrue(GenomicInput.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = { "17 7456845 A  t", "10    1012145   T>c", "0001  23423525   G/t", "22 23545643  C/a "// spaces or slash or greater than sign
    })
    void test_validCustomInput_valid_sub_sign(String input) {
        assertTrue(GenomicInput.isValid(input));
    }

    @Test
    void test_parse() {
        String inputStr = "x 123 a t";
        UserInput userInput = GenomicInput.parse(inputStr); // from previous VCFTest
        assertInput(true, inputStr, "X", 123, null, "A", "T", (GenomicInput)userInput);
    }

    private void assertInput(boolean valid, String inputStr, String chr, Integer pos, String id, String ref, String alt,
                                   GenomicInput actual) {
        assertEquals(valid, actual.isValid());
        assertEquals(inputStr, actual.getInputStr());
        assertEquals(chr, actual.getChr());
        assertEquals(pos, actual.getPos());
        assertEquals(id, actual.getId());
        assertEquals(ref, actual.getRef());
        assertEquals(alt, actual.getAlt());
    }
}