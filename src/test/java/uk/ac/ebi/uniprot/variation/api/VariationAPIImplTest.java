package uk.ac.ebi.uniprot.variation.api;

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
import uk.ac.ebi.protvar.resolver.AppTestConfig;
import uk.ac.ebi.protvar.utils.TestUtils;
import uk.ac.ebi.uniprot.variation.model.DataServiceVariation;

import uk.ac.ebi.uniprot.common.Common;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { AppTestConfig.class })
public class VariationAPIImplTest {
    @Mock
    RestTemplate variantRestTemplate;

    @InjectMocks
    VariationAPIImpl variationAPI;

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
        UriBuilder uriBuilder = uriBuilderFactory.builder().path(Common.URL_PATH_HGVS).path(hgvs);
        Mockito.when(variantRestTemplate.getForEntity(uriBuilder.build(), DataServiceVariation[].class))
                .thenReturn(varResp);
        DataServiceVariation[] dsv = variationAPI.getVariationByParam(hgvs, Common.URL_PATH_HGVS);
        assertEquals(4, dsv.length);
    }

    @Test
    void testGetColocatedVariation() throws IOException {
        ResponseEntity<DataServiceVariation[]> varResp = new ResponseEntity<>(
                TestUtils.getVariation("src/test/resources/jsons/variation.json"), HttpStatus.OK);
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
        Mockito.when(variantRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
        UriBuilder uriBuilder = uriBuilderFactory.builder().queryParam(Common.PARAM_TAXID, Common.TAX_ID_HUMAN)
                .queryParam(Common.PARAM_ACCESSION, "P21802").queryParam(Common.PARAM_LOCATION, "89993420-89993420");

        Mockito.when(variantRestTemplate.getForEntity(uriBuilder.build(), DataServiceVariation[].class))
                .thenReturn(varResp);
        DataServiceVariation[] dsv = variationAPI.getVariationByAccession("P21802", "89993420-89993420");
        assertEquals(4, dsv.length);
    }
}
