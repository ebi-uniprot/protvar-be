package uk.ac.ebi.protvar.input.parser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import uk.ac.ebi.protvar.input.*;

public class VariantParserTest {
	@Test
	public void testParse_NullAndEmptyInputs() {
		VariantInput result = VariantParser.parse("");
		assertFalse(result.getErrors().isEmpty());
		assertTrue(result.getErrors().contains("Empty input"));

		result = VariantParser.parse("   ");
		assertFalse(result.getErrors().isEmpty());
		assertTrue(result.getErrors().contains("Empty input"));
	}

	@Test
	public void testParse_HGVSProtein() {
		VariantInput result = VariantParser.parse("NP_123456.1:p.R490S");

		assertTrue(result instanceof ProteinInput);
		assertEquals(VariantFormat.HGVS_PROTEIN, result.getFormat());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testParse_HGVSCoding() {
		VariantInput result = VariantParser.parse("NM_000001.1:c.123A>T");

		assertTrue(result instanceof HGVSCodingInput);
		assertEquals(VariantFormat.HGVS_CODING, result.getFormat());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testParse_HGVSGenomic() {
		VariantInput result = VariantParser.parse("NC_000001.11:g.123A>T");

		assertTrue(result instanceof GenomicInput);
		assertEquals(VariantFormat.HGVS_GENOMIC, result.getFormat());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testParse_SingleWordFormats() {
		// Assuming these parsers exist and follow similar structure
		VariantInput result = VariantParser.parse("rs123456");
		// Should be handled by DbsnpParser if it exists

		result = VariantParser.parse("RCV000123456");
		// Should be handled by ClinvarParser if it exists

		result = VariantParser.parse("COSV123456");
		// Should be handled by CosmicParser if it exists
	}

	@Test
	public void testParse_InvalidRefseq() {
		VariantInput result = VariantParser.parse("NG_123456.1:g.123A>T");

		assertFalse(result.getErrors().isEmpty());
		assertTrue(result.getErrors().contains(ErrorConstants.HGVS_G_REFSEQ_INVALID.toString()));
	}

	@Test
	public void testParse_UnrecognizedFormat() {
		VariantInput result = VariantParser.parse("completely_invalid_format_12345");

		assertFalse(result.getErrors().isEmpty());
		assertTrue(result.getErrors().contains("Unsupported format"));
	}

	@Test
	public void testParse_ParserOrder() {
		// Test that more specific parsers are called before less specific ones

		// This should be parsed as HGVS protein, not as a generic protein format
		VariantInput result = VariantParser.parse("NP_123456.1:p.R490S");
		assertEquals(VariantFormat.HGVS_PROTEIN, result.getFormat());

		// This should be parsed as HGVS coding, not as a generic format
		result = VariantParser.parse("NM_000001.1:c.123A>T");
		assertEquals(VariantFormat.HGVS_CODING, result.getFormat());
	}

	@Test
	public void testParse_ExceptionHandling() {
		// Test that exceptions are properly caught and handled
		// This would depend on your actual implementation details
		VariantInput result = VariantParser.parse("some_input_that_causes_exception");

		// Should not throw exception, should return invalid input with error
		assertNotNull(result);
		if (result.hasError()) {
			assertFalse(result.getErrors().isEmpty());
		}
	}
}
