package uk.ac.ebi.pepvep.repo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import uk.ac.ebi.pepvep.utils.TestUtils;
import uk.ac.ebi.pepvep.model.PDBeRequest;
import uk.ac.ebi.pepvep.model.UserInput;
import uk.ac.ebi.pepvep.model.api.DataServiceCoordinate;
import uk.ac.ebi.pepvep.model.api.DataServiceProtein;
import uk.ac.ebi.pepvep.model.api.DataServiceVariation;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class UniprotAPIRepoImplTest {

	private static final String PARAM_SIZE = "size";

	private static final String PARAM_OFFSET = "offset";

	private static final String PARAM_GENE = "gene";

	private static final String URL_PATH_HGVS = "hgvs/";

	private static final String PARAM_ACCESSION = "accession";

	private static final String TAX_ID_HUMAN = "9606";

	private static final String PARAM_TAXID = "taxid";

	private static final String PARAM_LOCATION = "location";

	private static final String PARAM_CHROMOSOME = "chromosome";

	@Mock
	RestTemplate variantRestTemplate;

	@Mock
	RestTemplate proteinRestTemplate;

	@Mock
	RestTemplate coordinateRestTemplate;

	@Mock
	RestTemplate pdbeRestTemplate;

	@InjectMocks
	UniprotAPIRepoImpl uniprotAPIRepo;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	void testGetVariation() throws IOException {
		ResponseEntity<DataServiceVariation[]> varResp = new ResponseEntity<>(
				TestUtils.getVariation("src/test/resources/jsons/variation.json"), HttpStatus.OK);
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(variantRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
		String hgvs = "NC_000014.9:g.89993420A>G";
		UriBuilder uriBuilder = uriBuilderFactory.builder().path(URL_PATH_HGVS).path(hgvs);
		Mockito.when(variantRestTemplate.getForEntity(uriBuilder.build(), DataServiceVariation[].class))
				.thenReturn(varResp);
		DataServiceVariation[] dsv = uniprotAPIRepo.getVariationByParam(hgvs, URL_PATH_HGVS);
		assertEquals(4, dsv.length);
	}

	@Test
	void testGetColocatedVariation() throws IOException {
		ResponseEntity<DataServiceVariation[]> varResp = new ResponseEntity<>(
				TestUtils.getVariation("src/test/resources/jsons/variation.json"), HttpStatus.OK);
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(variantRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
		UriBuilder uriBuilder = uriBuilderFactory.builder().queryParam(PARAM_TAXID, TAX_ID_HUMAN)
				.queryParam(PARAM_ACCESSION, "P21802").queryParam(PARAM_LOCATION, "89993420-89993420");

		Mockito.when(variantRestTemplate.getForEntity(uriBuilder.build(), DataServiceVariation[].class))
				.thenReturn(varResp);
		DataServiceVariation[] dsv = uniprotAPIRepo.getVariationByAccession("P21802", "89993420-89993420");
		assertEquals(4, dsv.length);
	}

	@Test
	void testGetProtein() throws IOException {
		ResponseEntity<DataServiceProtein[]> proteinResp = new ResponseEntity<>(
				TestUtils.getProtein("src/test/resources/jsons/protein.json"), HttpStatus.OK);

		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(proteinRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
		UriBuilder uriBuilder = uriBuilderFactory.builder().queryParam("accession", "Q9NUW8").queryParam(PARAM_TAXID,
				TAX_ID_HUMAN);

		Mockito.when(proteinRestTemplate.getForEntity(uriBuilder.build(), DataServiceProtein[].class))
				.thenReturn(proteinResp);
		DataServiceProtein[] dsp = uniprotAPIRepo.getProtein("Q9NUW8");
		assertEquals(4, dsp.length);
	}

	@Test
	void testGetGene() throws Exception {
		ResponseEntity<DataServiceCoordinate[]> geneResp = new ResponseEntity<>(
				TestUtils.getGene("src/test/resources/jsons/coordinate.json"), HttpStatus.OK);
		UserInput input = UserInput.getInput("14 89993420 89993420 A/G . . .");

		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(coordinateRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);

		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add(PARAM_CHROMOSOME, input.getChromosome());
		String location = input.getStart() + "-" + input.getStart();
		queryParams.add("location", location);
		queryParams.add("taxid", "9606");

		UriBuilder uriBuilder = uriBuilderFactory.builder().queryParams(queryParams);

		Mockito.when(coordinateRestTemplate.getForEntity(uriBuilder.build(), DataServiceCoordinate[].class))
				.thenReturn(geneResp);
		DataServiceCoordinate[] dsc = uniprotAPIRepo.getGene(input);
		assertEquals(5, dsc.length);
	}

	@Test
	void testGetCoordinates() throws Exception {
		ResponseEntity<DataServiceCoordinate[]> geneResp = new ResponseEntity<>(
				TestUtils.getGene("src/test/resources/merge/coordinate_P68431.json"), HttpStatus.OK);

		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(coordinateRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);

		String geneName = "TDP1";
		String chromosome = "14";
		int offset = 0;
		int pageSize = 5;
		String location = "6125153";

		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add(PARAM_GENE, geneName);
		queryParams.add(PARAM_TAXID, TAX_ID_HUMAN);
		queryParams.add(PARAM_CHROMOSOME, chromosome);
		queryParams.add(PARAM_OFFSET, String.valueOf(offset));
		queryParams.add(PARAM_SIZE, String.valueOf(pageSize));
		queryParams.add(PARAM_LOCATION, location);

		UriBuilder uriBuilder = uriBuilderFactory.builder().queryParams(queryParams);

		Mockito.when(coordinateRestTemplate.getForEntity(uriBuilder.build(), DataServiceCoordinate[].class))
				.thenReturn(geneResp);

		DataServiceCoordinate[] dsc = uniprotAPIRepo.getCoordinates(geneName, chromosome, offset, pageSize, location);
		assertEquals(1, dsc.length);
	}

	@Test
	void testGetCoordinateByAccession() throws Exception {
		ResponseEntity<DataServiceCoordinate[]> geneResp = new ResponseEntity<>(
				TestUtils.getGene("src/test/resources/merge/coordinate_P68431.json"), HttpStatus.OK);

		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(coordinateRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);

		String accession = "P68431";

		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add(PARAM_ACCESSION, accession);
		queryParams.add(PARAM_TAXID, TAX_ID_HUMAN);

		UriBuilder uriBuilder = uriBuilderFactory.builder().queryParams(queryParams);

		Mockito.when(coordinateRestTemplate.getForEntity(uriBuilder.build(), DataServiceCoordinate[].class))
				.thenReturn(geneResp);

		DataServiceCoordinate[] dsc = uniprotAPIRepo.getCoordinateByAccession(accession);
		assertEquals(1, dsc.length);
	}

	@Test
	void testGetStructure() throws Exception {
		ResponseEntity<Object[]> pdbeResp = new ResponseEntity<>(
				TestUtils.getStructure("src/test/resources/jsons/pdbe.json"), HttpStatus.OK);

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);
		PDBeRequest request = new PDBeRequest();
		request.setAccession("P21802");
		request.setPositions(List.of("493"));
		List<PDBeRequest> requests = new ArrayList<>();
		requests.add(request);
		HttpEntity<List<PDBeRequest>> requestEntity = new HttpEntity<>(requests, headers);

		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(pdbeRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
		UriBuilder uriBuilder = uriBuilderFactory.builder().path("graph-api/pepvep/summary_stats");

		Mockito.when(pdbeRestTemplate.postForEntity(uriBuilder.build(), requestEntity, Object[].class))
				.thenReturn(pdbeResp);
		Object[] pdbe = uniprotAPIRepo.getPDBe(requests);
		assertEquals(4, pdbe.length);
	}

	@Test
	void testGetUniprotAccession() throws Exception {
		Object pdbeResp = TestUtils.getMapping("src/test/resources/pdbe_mapping.json");
		ResponseEntity<Object> mappingRes = new ResponseEntity<>(pdbeResp, HttpStatus.OK);

		String accession = "6n0d";

		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(pdbeRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
		UriBuilder uriBuilder = uriBuilderFactory.builder().path("api/mappings/uniprot/").path(accession);

		Mockito.when(pdbeRestTemplate.getForEntity(uriBuilder.build(), Object.class)).thenReturn(mappingRes);
		String uniprotAccession = uniprotAPIRepo.getUniproAccession(accession);
		assertEquals("Q9NUW8", uniprotAccession);

	}

	@Test
	void testGetUniprotNullAccession() {
		ResponseEntity<Object> mappingRes = null;

		String accession = "6n0d";

		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
		Mockito.when(pdbeRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
		UriBuilder uriBuilder = uriBuilderFactory.builder().path("api/mappings/uniprot/").path(accession);

		Mockito.when(pdbeRestTemplate.getForEntity(uriBuilder.build(), Object.class)).thenReturn(mappingRes);
		String uniprotAccession = uniprotAPIRepo.getUniproAccession(accession);
		assertNull(uniprotAccession);

	}

}
