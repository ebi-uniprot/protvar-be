package uk.ac.ebi.uniprot.coordinates.api;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.protvar.resolver.AppTestConfig;
import uk.ac.ebi.protvar.utils.TestUtils;
import uk.ac.ebi.uniprot.coordinates.model.DataServiceCoordinate;
import uk.ac.ebi.uniprot.common.Common;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { AppTestConfig.class })
public class CoordinatesAPIImplTest {

    @Mock
    RestTemplate coordinateRestTemplate;


    @InjectMocks
    CoordinatesAPIImpl coordinatesAPI;


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

/*
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
	}*/

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
        queryParams.add(Common.PARAM_GENE, geneName);
        queryParams.add(Common.PARAM_TAXID, Common.TAX_ID_HUMAN);
        queryParams.add(Common.PARAM_CHROMOSOME, chromosome);
        queryParams.add(Common.PARAM_OFFSET, String.valueOf(offset));
        queryParams.add(Common.PARAM_SIZE, String.valueOf(pageSize));
        queryParams.add(Common.PARAM_LOCATION, location);

        UriBuilder uriBuilder = uriBuilderFactory.builder().queryParams(queryParams);

        Mockito.when(coordinateRestTemplate.getForEntity(uriBuilder.build(), DataServiceCoordinate[].class))
                .thenReturn(geneResp);

        DataServiceCoordinate[] dsc = coordinatesAPI.getCoordinates(geneName, chromosome, offset, pageSize, location);
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
        queryParams.add(Common.PARAM_ACCESSION, accession);
        queryParams.add(Common.PARAM_TAXID, Common.TAX_ID_HUMAN);

        UriBuilder uriBuilder = uriBuilderFactory.builder().queryParams(queryParams);

        Mockito.when(coordinateRestTemplate.getForEntity(uriBuilder.build(), DataServiceCoordinate[].class))
                .thenReturn(geneResp);

        DataServiceCoordinate[] dsc = coordinatesAPI.getCoordinateByAccession(accession);
        assertEquals(1, dsc.length);
    }

}
