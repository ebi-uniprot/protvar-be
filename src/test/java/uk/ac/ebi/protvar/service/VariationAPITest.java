package uk.ac.ebi.protvar.service;

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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.protvar.api.VariationAPI;
import uk.ac.ebi.protvar.resolver.AppTestConfig;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.TestUtils;
import uk.ac.ebi.uniprot.domain.features.ProteinFeatureInfo;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { AppTestConfig.class })
public class VariationAPITest {
    @Mock
    RestTemplate variationRestTemplate;

    @InjectMocks
    VariationAPI variationAPI;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testGetVariation() throws IOException {
        ResponseEntity<ProteinFeatureInfo[]> varResp = new ResponseEntity<>(
                TestUtils.getVariation("src/test/resources/jsons/variation.json"), HttpStatus.OK);
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
        Mockito.when(variationRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
        String hgvs = "NC_000014.9:g.89993420A>G";
        UriBuilder uriBuilder = uriBuilderFactory.builder().path(Constants.URL_PATH_HGVS).path(hgvs);
        Mockito.when(variationRestTemplate.getForEntity(uriBuilder.build(), ProteinFeatureInfo[].class))
                .thenReturn(varResp);
        ProteinFeatureInfo[] dsv = variationAPI.getVariationByParam(hgvs, Constants.URL_PATH_HGVS);
        assertEquals(4, dsv.length);
    }

    @Test
    void testGetColocatedVariation() throws IOException {
        ResponseEntity<ProteinFeatureInfo[]> varResp = new ResponseEntity<>(
                TestUtils.getVariation("src/test/resources/jsons/variation.json"), HttpStatus.OK);
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
        Mockito.when(variationRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
        UriBuilder uriBuilder = uriBuilderFactory.builder().queryParam(Constants.PARAM_TAXID, Constants.TAX_ID_HUMAN)
                .queryParam(Constants.PARAM_ACCESSION, "P21802").queryParam(Constants.PARAM_LOCATION, 89993420);

        Mockito.when(variationRestTemplate.getForEntity(uriBuilder.build(), ProteinFeatureInfo[].class))
                .thenReturn(varResp);
        ProteinFeatureInfo[] dsv = variationAPI.getVariation("P21802", 89993420);
        assertEquals(4, dsv.length);
    }
}
