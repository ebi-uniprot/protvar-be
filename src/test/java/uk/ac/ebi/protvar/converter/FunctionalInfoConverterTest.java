package uk.ac.ebi.protvar.converter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import uk.ac.ebi.protvar.utils.TestUtils;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;

@Service
public class FunctionalInfoConverterTest {

	FunctionalInfoConverter converter = new FunctionalInfoConverter();

	@Test
	public void testApply() throws Exception {
		String data = Files.readString(Path.of("src/test/resources/protein.json"));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		UPEntry[] entries = gson.fromJson(data, UPEntry[].class);
//		assertEquals(1, protein.getCanonicalIsoforms().size());
//		assertEquals(Arrays.asList("Q9NUW8-1"), protein.getCanonicalIsoforms());
//		assertEquals("Q9NUW8", protein.getCanonicalAccession());

//		assertEquals(2, protein.getDbReferences().size());
//		assertEquals("ENST00000335725", protein.getDbReferences().get(0).getId());
//		assertEquals("ENST00000393454", protein.getDbReferences().get(1).getId());
//		assertEquals("HGNC:18884", protein.getHgncId());

//		assertEquals(2, protein.getFeatures().size());
//		assertEquals("Tyrosyl-DNA phosphodiesterase 1", protein.getName().getFull());
//		assertEquals("Tyr-DNA phosphodiesterase 1", protein.getName().getShortName());
//		assertEquals("Met/Arg", protein.getThreeLetterCodes());
//		assertEquals("M/R", protein.getVariant());
	}

	@Test
	public void testNullAltProducts() throws Exception {
		String data = Files.readString(Path.of("src/test/resources/protein_E7EPD8.json"));
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		UPEntry[] entries = gson.fromJson(data, UPEntry[].class);
		FunctionalInfo protein = converter.convert(entries[0]);
//		assertNull(protein.getCanonicalIsoforms());
//		assertNull(protein.getCanonicalAccession());
//
//		assertEquals(1, protein.getDbReferences().size());
//		assertEquals("ENST00000393452", protein.getDbReferences().get(0).getId());

//		assertEquals(1, protein.getFeatures().size());
		assertNull(protein.getName());
//		assertEquals("Ser/Phe", protein.getThreeLetterCodes());
//		assertEquals("S/F", protein.getVariant());
	}

	// From deleted ProteinHelperTest
	@org.junit.jupiter.api.Test
	public void testSwissProtCanonical() throws IOException {
		UPEntry[] entries = TestUtils.getProtein("src/test/resources/protein_E7EPD8.json");
		FunctionalInfoConverter converter = new FunctionalInfoConverter();

		FunctionalInfo protein = converter.convert(entries[0]);
		protein.setType("Swiss-Prot");
		System.out.println("Protein: " + protein);
//		assertEquals("E7EPD8", protein.getCanonicalAccession());
//		assertTrue(protein.isCanonical());
	}

	@org.junit.jupiter.api.Test
	public void testCanonicalFalse() throws IOException {
		UPEntry[] entries = TestUtils.getProtein("src/test/resources/protein_E7EPD8.json");
		FunctionalInfoConverter converter = new FunctionalInfoConverter();
		FunctionalInfo protein = converter.convert(entries[0]);
		System.out.println("Protein: " + protein);
//		assertNull(protein.getCanonicalAccession());
//		assertFalse(protein.isCanonical());
	}

	@org.junit.jupiter.api.Test
	public void testNonSwissProtCanonical() throws IOException {
		UPEntry[] entries = TestUtils.getProtein("src/test/resources/jsons/protein.json");
		FunctionalInfoConverter converter = new FunctionalInfoConverter();
		FunctionalInfo protein = converter.convert(entries[1]);
//		protein.getDbReferences().get(0).setIsoform("G3V2F4-1");
//		protein.setCanonicalIsoforms(Arrays.asList("G3V2F4-1"));
//		protein.setIsoform("G3V2F4-1");
		System.out.println("Protein: " + protein);
		assertEquals("TrEMBL", protein.getType());
//		assertTrue(protein.isCanonical());
	}

}
