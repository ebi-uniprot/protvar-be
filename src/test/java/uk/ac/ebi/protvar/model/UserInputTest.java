package uk.ac.ebi.protvar.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.protvar.utils.Constants;

public class UserInputTest {

	@Test
	void testGetInput() {
		String input = "21 25891796 25891797 C/T . . .";
		UserInput actual = UserInput.getInput(input);
		assertEquals("21", actual.getChromosome());
		assertEquals(25891796, actual.getStart());
		assertEquals("C", actual.getRef());
		assertEquals("T", actual.getAlt());
		assertEquals("21 25891796 25891797 C/T . . .", actual.getInputString());
	}

	@Test
	void testGetInputTab() {
		String input = "21\t25891796\t25891797\tC/T . . .";
		UserInput actual = UserInput.getInput(input);
		assertEquals("21", actual.getChromosome());
		assertEquals(25891796, actual.getStart());
		assertEquals("C", actual.getRef());
		assertEquals("T", actual.getAlt());
		assertEquals("21\t25891796\t25891797\tC/T . . .", actual.getInputString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
		"17", "18", "19", "20", "21", "22"})
	void numberChromosomeTest(String chromosome) {
		assertEquals(chromosome, UserInput.convertChromosome(chromosome));
	}

	@ParameterizedTest
	@ValueSource(strings = {"x", "X", " x", "x ", " X "})
	void xChromosomeTest(String chromosome) {
		assertEquals("X", UserInput.convertChromosome(chromosome));
	}

	@ParameterizedTest
	@ValueSource(strings = {"y", "Y", " y", "Y ", " Y "})
	void yChromosomeTest(String chromosome) {
		assertEquals("Y", UserInput.convertChromosome(chromosome));
	}

	@ParameterizedTest
	@ValueSource(strings = {"chrM", "CHRM", "mitochondria", " mitoCHondria", "mitochondrion", "MITOchondrion ", "MT ", "mtDNA", "mit"})
	void mtChromosomeTest(String chromosome) {
		Assertions.assertEquals(UserInput.DB_MT_CHROMOSOME, UserInput.convertChromosome(chromosome));
	}

	@ParameterizedTest
	@ValueSource(strings = {"23", "24", "a", "b", " ",""})
	void invalidChromosomeTest(String chromosome) {
		Assertions.assertEquals(Constants.NA, UserInput.convertChromosome(chromosome));
	}

	@Test
	void nullChromosomeTest() {
		Assertions.assertEquals(Constants.NA, UserInput.convertChromosome(null));
	}

	@ParameterizedTest
	@NullAndEmptySource
	void alleleEmptyNullTest(String allele) {
		assertFalse(UserInput.isAllele(allele));
	}

	@ParameterizedTest
	@ValueSource(strings = {"A", "C", "G", "T", "a", "c", " g", "t "})
	void alleleValid(String allele) {
		assertTrue(UserInput.isAllele(allele));
	}

	@ParameterizedTest
	@ValueSource(strings = {"B", "D", "H", "K", "z", "x", " e", "m "})
	void alleleInValid(String allele) {
		assertFalse(UserInput.isAllele(allele));
	}

	@ParameterizedTest
	@NullAndEmptySource
	void referenceAndAlternativeCombineEmptyNullTest(String allele) {
		assertFalse(UserInput.isReferenceAndAlternativeAllele(allele));
	}

	@ParameterizedTest
	@ValueSource(strings = {"A/C", "C/G", "G/T", "T/A", " a/g ", "c/t", " g/a", "t/t "})
	void referenceAndAlternativeCombineValid(String allele) {
		assertTrue(UserInput.isReferenceAndAlternativeAllele(allele));
	}

	@ParameterizedTest
	@ValueSource(strings = {"A", "AC", "ACG", "K/B", "a / c", "s\\d", " e$d", "m a", "N/A"})
	void referenceAndAlternativeCombineInValid(String allele) {
		assertFalse(UserInput.isReferenceAndAlternativeAllele(allele));
	}
}
