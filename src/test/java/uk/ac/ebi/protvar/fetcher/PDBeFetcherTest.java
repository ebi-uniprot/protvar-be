package uk.ac.ebi.protvar.fetcher;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import uk.ac.ebi.protvar.resolver.AppTestConfig;

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
