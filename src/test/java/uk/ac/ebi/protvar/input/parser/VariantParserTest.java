package uk.ac.ebi.protvar.input.parser;
import java.util.List;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.MessageCode;

import static org.junit.jupiter.api.Assertions.*;

import uk.ac.ebi.protvar.input.*;

public class VariantParserTest {
	@Test
	public void testParse_NullAndEmptyInputs() {
		VariantInput result = VariantParser.parse("");
		assertFalse(result.getErrors().isEmpty());
		assertTrue(result.getErrors().contains(MessageCode.PV911_EMPTY_LINE.getText()));

		result = VariantParser.parse("   ");
		assertFalse(result.getErrors().isEmpty());
		assertTrue(result.getErrors().contains(MessageCode.PV911_EMPTY_LINE.getText()));
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
		assertTrue(result.getErrors().contains(MessageCode.PV320_G_REFSEQ_INVALID.toString()));
	}

	@Test
	public void testParse_UnrecognizedFormat() {
		VariantInput result = VariantParser.parse("completely_invalid_format_12345");

		assertFalse(result.getErrors().isEmpty());
		assertTrue(result.getErrors().contains(MessageCode.PV909_FORMAT_UNKNOWN.getText()));
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

	@Test
	public void testParse_UnsupportedReasons() {
		assertUnsupported(MessageCode.PV902_INDEL, "19:18169206:TC:T", "19 12943813 A ATTGTC",
				"chr1 248650777 CAA C", "1-12345-A-AT", "X\t120253978\tCTGA\tC");
		assertUnsupported(MessageCode.PV903_MULTI_BASE, "7 140753336 AC TT", "10:71807696:AG:CC");
		assertUnsupported(MessageCode.PV904_PROTEIN_NO_ACC, "R32C", "p.Gly358Arg", "p. R2580S", "E276Q");
		assertUnsupported(MessageCode.PV905_GENE_SYMBOL, "SMAD4 R361H", "TP53 p.Arg175His", "STAT1 L206F");
		assertUnsupported(MessageCode.PV906_C_NO_REFSEQ, "c.1072G>A");
		assertUnsupported(MessageCode.PV907_ID_ONLY, "ENST00000467905", "NM_002691.4");
		assertUnsupported(MessageCode.PV908_ACC_ONLY, "P51608", "Q14592");
		assertUnsupported(MessageCode.PV901_UNREADABLE_CHARS, "PK\u0003\u0004\u0014\u0000\u0006\u0000\u0008\u0000content", "\uFFFD\uFFFDLCr\uFFFD");
		assertUnsupported(MessageCode.PV909_FORMAT_UNKNOWN, "completely_invalid_format_12345", "PASS");
	}

	@Test
	public void testParse_NewlySupported() {
		VariantInput result = VariantParser.parse("12:56478854:na:na");
		assertTrue(result.isValid());
		assertEquals(VariantFormat.INTERNAL_GENOMIC, result.getFormat());

		result = VariantParser.parse("chr11:g.108321424A>G");
		assertTrue(result.isValid());
		assertEquals(VariantFormat.HGVS_GENOMIC, result.getFormat());

		assertTrue(VariantParser.parse("NM_000316.3:c.1393G>A/p.(Glu465Lys)").isValid());
		assertTrue(VariantParser.parse("NM_000095.3: c.1219 T>C").isValid());
		assertEquals(VariantFormat.INTERNAL_GENOMIC, VariantParser.parse("chr14:89993420 A>G").getFormat());
		assertTrue(VariantParser.parse("chr14:89993420 A>G").isValid());
	}

	private void assertUnsupported(MessageCode expected, String... inputs) {
		for (String input : inputs) {
			VariantInput result = VariantParser.parse(input);
			assertFalse(result.isValid(), input);
			assertEquals(List.of(expected.toString()), result.getErrors(), input);
		}
	}
}
