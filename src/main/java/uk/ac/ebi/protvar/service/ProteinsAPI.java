package uk.ac.ebi.protvar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;

@Service
public class ProteinsAPI {
    private static final Logger logger = LoggerFactory.getLogger(ProteinsAPI.class);

    @Autowired
    @Qualifier("proteinsRestTemplate")
    private RestTemplate proteinsRestTemplate;

    public UPEntry[] getProtein(String accessions) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.proteinsRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder().queryParam(Constants.PARAM_ACCESSION, accessions).queryParam(Constants.PARAM_TAXID,
                Constants.TAX_ID_HUMAN);
        logger.info("Proteins API call: {}", uriBuilder.build());
        ResponseEntity<UPEntry[]> response = this.proteinsRestTemplate.getForEntity(uriBuilder.build(),
                UPEntry[].class);
        return response.getBody();
    }

}
