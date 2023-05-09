package uk.ac.ebi.uniprot.proteins.api;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;
import uk.ac.ebi.uniprot.common.Common;

@Repository
@AllArgsConstructor
public class ProteinsAPIImpl implements ProteinsAPI {
    private static final Logger logger = LoggerFactory.getLogger(ProteinsAPIImpl.class);

    private RestTemplate proteinRestTemplate;

    @Override
    public DataServiceProtein[] getProtein(String accessions) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.proteinRestTemplate.getUriTemplateHandler();
        logger.info("Calling protein for accessions -> {}", accessions);
        UriBuilder uriBuilder = handler.builder().queryParam(Common.PARAM_ACCESSION, accessions).queryParam(Common.PARAM_TAXID,
                Common.TAX_ID_HUMAN);
        ResponseEntity<DataServiceProtein[]> response = this.proteinRestTemplate.getForEntity(uriBuilder.build(),
                DataServiceProtein[].class);
        return response.getBody();
    }
}
