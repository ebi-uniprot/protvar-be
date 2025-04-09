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
import uk.ac.ebi.protvar.resolver.AppTestConfig;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.TestUtils;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { AppTestConfig.class })
public class ProteinsAPITest {


    @Mock
    RestTemplate proteinsRestTemplate;

    @InjectMocks
    ProteinsAPI proteinsAPI;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testGetProtein() throws IOException {
        ResponseEntity<UPEntry[]> proteinResp = new ResponseEntity<>(
                TestUtils.getProtein("src/test/resources/jsons/protein.json"), HttpStatus.OK);

        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory("");
        Mockito.when(proteinsRestTemplate.getUriTemplateHandler()).thenReturn(uriBuilderFactory);
        UriBuilder uriBuilder = uriBuilderFactory.builder().queryParam("accession", "Q9NUW8").queryParam(Constants.PARAM_TAXID,
                Constants.TAX_ID_HUMAN);

        Mockito.when(proteinsRestTemplate.getForEntity(uriBuilder.build(), UPEntry[].class))
                .thenReturn(proteinResp);
        UPEntry[] entries = proteinsAPI.getProtein("Q9NUW8");
        assertEquals(4, entries.length);
    }

}