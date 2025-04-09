package uk.ac.ebi.protvar.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;

@Service
public class UPEntry2FunctionalInfoConverterTest {

	UPEntry2FunctionalInfoConverter converter = new UPEntry2FunctionalInfoConverter();

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

}
