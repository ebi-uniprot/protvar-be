package uk.ac.ebi.pepvep.fetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

import uk.ac.ebi.pepvep.exception.ServiceException;
import uk.ac.ebi.pepvep.model.Gene;
import uk.ac.ebi.pepvep.resolver.AppTestConfig;

@ActiveProfiles({ "test" })
@EnableAutoConfiguration
@ComponentScan(basePackages = { "uk.ac.ebi.uniprot.pepvep" })
@SpringBootTest(classes = { AppTestConfig.class })
public class GenomicFetcherIT {

	@BeforeEach
	public void setUp() throws IOException {
		MockitoAnnotations.initMocks(this);
	}

	@Autowired
	GenomicFetcher geneFetcher;

	@Test
	public void testSearchGene() throws ServiceException {

		String geneName = "TDP1";
		String chromosome = "14";
		int offset = 0;
		int pageSize = 5;
		String location = "6125153";

		Map<String, List<Gene>> accessionGenesMap = geneFetcher.searchGene(geneName, chromosome, offset, pageSize,
				location);
		List<Gene> genes = accessionGenesMap.get("P68431");
		assertEquals(9, genes.size());
	}

}
