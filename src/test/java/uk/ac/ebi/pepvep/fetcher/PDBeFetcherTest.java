package uk.ac.ebi.pepvep.fetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

import uk.ac.ebi.pepvep.resolver.AppTestConfig;

@ActiveProfiles({ "test" })
@EnableAutoConfiguration
@ComponentScan(basePackages = { "uk.ac.ebi.uniprot.pepvep" })
@SpringBootTest(classes = { AppTestConfig.class })
public class PDBeFetcherTest {

	@BeforeEach
	public void setUp() throws IOException {
		MockitoAnnotations.initMocks(this);
	}

	@Autowired
	PDBeFetcher pdBeFetcher;

//	@Test
//	public void testSearchGene() throws ServiceException {
//
//		String accession = "P68431";
//		Long position = 14l;
//		Map<Long, Object> positionStructureMap = pdBeFetcher.fetchByAccession(accession, position);
//		assertEquals(1, positionStructureMap.size());
//	}

}
