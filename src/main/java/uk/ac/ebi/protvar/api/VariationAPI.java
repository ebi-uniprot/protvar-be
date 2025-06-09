package uk.ac.ebi.protvar.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.uniprot.domain.features.ProteinFeatureInfo;

@Service
public class VariationAPI {
    private static final Logger logger = LoggerFactory.getLogger(VariationAPI.class);
    public static final int PARTITION_SIZE = 100;

    @Autowired
    @Qualifier("variationRestTemplate")
    private RestTemplate variationRestTemplate;

    public ProteinFeatureInfo[] getVariationByParam(String paramValue, String pathParam) {
        logger.info("Calling variation: {}", paramValue);
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variationRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder().path(pathParam).path(paramValue);

        ResponseEntity<ProteinFeatureInfo[]> response = this.variationRestTemplate.getForEntity(uriBuilder.build(),
                ProteinFeatureInfo[].class);
        return response.getBody();
    }

    public ProteinFeatureInfo[] getVariation(String accession, int location) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variationRestTemplate.getUriTemplateHandler();
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add(Constants.PARAM_TAXID, Constants.TAX_ID_HUMAN);
        queryParams.add(Constants.PARAM_ACCESSION, accession);
        queryParams.add(Constants.PARAM_LOCATION, String.valueOf(location));
        UriBuilder uriBuilder = handler.builder().queryParams(queryParams);
        logger.info("Variation API call: {}", uriBuilder.build());
        ResponseEntity<ProteinFeatureInfo[]> response = this.variationRestTemplate.getForEntity(uriBuilder.build(),
                ProteinFeatureInfo[].class);
        return response.getBody();
    }

    public ProteinFeatureInfo[] getVariation(String accessions) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variationRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder().queryParam(Constants.PARAM_ACCESSION, accessions).queryParam(Constants.PARAM_TAXID,
                Constants.TAX_ID_HUMAN);
        logger.info("Variation API call: {}", uriBuilder.build());
        ResponseEntity<ProteinFeatureInfo[]> response = this.variationRestTemplate.getForEntity(uriBuilder.build(),
                ProteinFeatureInfo[].class);
        return response.getBody();
    }


    /**
     * URI structure
     * BASE/accession_locations/ACC1:POS1|ACC2:POS2
     * @param accLocs
     * @return
     */
    public ProteinFeatureInfo[] getVariationAccessionLocations(String accLocs) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variationRestTemplate.getUriTemplateHandler();
        UriBuilder uriBuilder = handler.builder().path("accession_locations/").path(accLocs);
        logger.info("Variation API call: {}", uriBuilder.build());
        ResponseEntity<ProteinFeatureInfo[]> response = this.variationRestTemplate.getForEntity(uriBuilder.build(),
                ProteinFeatureInfo[].class);
        return response.getBody();
    }

}
