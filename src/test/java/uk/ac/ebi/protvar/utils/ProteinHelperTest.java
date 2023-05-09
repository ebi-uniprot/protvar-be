package uk.ac.ebi.protvar.utils;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import uk.ac.ebi.protvar.resolver.AppTestConfig;
import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;
import uk.ac.ebi.protvar.converter.ProteinsAPI2ProteinConverter;
import uk.ac.ebi.protvar.model.response.Protein;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { AppTestConfig.class })
public class ProteinHelperTest {

	@Test
	public void testSwissProtCanonical() throws IOException {
		DataServiceProtein[] dsp = TestUtils.getProtein("src/test/resources/protein_E7EPD8.json");
		ProteinsAPI2ProteinConverter converter = new ProteinsAPI2ProteinConverter();

		Protein protein = converter.fetch(dsp[0]);
		protein.setType("Swiss-Prot");
		System.out.println("Protein: " + protein);
//		assertEquals("E7EPD8", protein.getCanonicalAccession());
//		assertTrue(protein.isCanonical());
	}

	@Test
	public void testCanonicalFalse() throws IOException {
		DataServiceProtein[] dsp = TestUtils.getProtein("src/test/resources/protein_E7EPD8.json");
		ProteinsAPI2ProteinConverter converter = new ProteinsAPI2ProteinConverter();
		Protein protein = converter.fetch(dsp[0]);
		System.out.println("Protein: " + protein);
//		assertNull(protein.getCanonicalAccession());
//		assertFalse(protein.isCanonical());
	}

	@Test
	public void testNonSwissProtCanonical() throws IOException {
		DataServiceProtein[] dsp = TestUtils.getProtein("src/test/resources/jsons/protein.json");
		ProteinsAPI2ProteinConverter converter = new ProteinsAPI2ProteinConverter();
		Protein protein = converter.fetch(dsp[1]);
//		protein.getDbReferences().get(0).setIsoform("G3V2F4-1");
//		protein.setCanonicalIsoforms(Arrays.asList("G3V2F4-1"));
//		protein.setIsoform("G3V2F4-1");
		System.out.println("Protein: " + protein);
		assertEquals("TrEMBL", protein.getType());
//		assertTrue(protein.isCanonical());
	}

}
