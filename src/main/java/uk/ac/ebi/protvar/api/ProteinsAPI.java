package uk.ac.ebi.protvar.api;

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

import java.util.Collection;

@Service
public class ProteinsAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProteinsAPI.class);
    public static final int PARTITION_SIZE = 100;

    @Autowired
    @Qualifier("proteinsRestTemplate")
    private RestTemplate proteinsRestTemplate;

    public UPEntry[] getProtein(Collection<String> accessions) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.proteinsRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder()
                .queryParam(Constants.PARAM_ACCESSION, String.join(",", accessions))
                .queryParam(Constants.PARAM_TAXID, Constants.TAX_ID_HUMAN);

        LOGGER.info("Proteins API call: {}", uriBuilder.build());

        ResponseEntity<UPEntry[]> response = this.proteinsRestTemplate.getForEntity(uriBuilder.build(), UPEntry[].class);
        return response.getBody();
    }

}
