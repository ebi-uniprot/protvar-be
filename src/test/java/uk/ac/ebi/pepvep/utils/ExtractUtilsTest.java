package uk.ac.ebi.pepvep.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ExtractUtilsTest {

	@ParameterizedTest
	@CsvSource({ "NC_000010.11:g.104259641G>C,104259641", "CHR_HSCHR6_MHC_SSTO_CTG1:g.31679057dup,31679057",
			"NC_000020.11:g.43465875_43465877del,43465875", "LRG_389:g.19405_19406del,19405" })
	void testExtractLocation(String hgvs, String expected) {
		Long actual = ExtractUtils.extractLocation(hgvs);
		assertEquals(Long.parseLong(expected), actual);
	}

	@ParameterizedTest
	@CsvSource({ "NC_000010.11:g.104259641G>C|NULL,G>C", "CHR_HSCHR6_MHC_SSTO_CTG1:g.31679057dup|TAC/TAAC,TAC>TAAC",
			"NC_000020.11:g.43465875_43465877del|TTAAGA/TGA,TTAAGA>TGA", "LRG_389:g.19405_19406del|NULL,NULL" })
	void testExtractAllele(String hgvsCodon, String expected) {
		String[] hgvsCodons = hgvsCodon.split("\\|");

		String actual = ExtractUtils.extractAllele(hgvsCodons[0], hgvsCodons[1]);
		if ("NULL".equals(actual))
			actual = null;
		if ("NULL".equals(expected))
			expected = null;
		assertEquals(expected, actual);
	}
}
