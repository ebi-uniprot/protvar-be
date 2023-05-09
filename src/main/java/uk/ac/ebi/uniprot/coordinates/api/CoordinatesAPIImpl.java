package uk.ac.ebi.uniprot.coordinates.api;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import uk.ac.ebi.uniprot.coordinates.model.DataServiceCoordinate;
import uk.ac.ebi.uniprot.common.Common;

@Repository
@AllArgsConstructor
public class CoordinatesAPIImpl implements CoordinatesAPI {

    private static final Logger logger = LoggerFactory.getLogger(LoggerFactory.class);

    private RestTemplate coordinateRestTemplate;
    @Override
    public DataServiceCoordinate[] getCoordinateByAccession(String accession) {
        logger.info("Calling gene: {}", accession);
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.coordinateRestTemplate
                .getUriTemplateHandler();
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add(Common.PARAM_ACCESSION, accession);
        queryParams.add(Common.PARAM_TAXID, Common.TAX_ID_HUMAN);
        UriBuilder uriBuilder = handler.builder().queryParams(queryParams);
        ResponseEntity<DataServiceCoordinate[]> response = this.coordinateRestTemplate.getForEntity(uriBuilder.build(),
                DataServiceCoordinate[].class);
        return response.getBody();
    }


    @Override
    public DataServiceCoordinate[] getCoordinates(String geneName, String chromosome, int offset, int pageSize,
                                                  String location) {
        DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.coordinateRestTemplate
                .getUriTemplateHandler();
        MultiValueMap<String, String> queryParams = buildQueryParam(geneName, chromosome, offset, pageSize, location);
        UriBuilder uriBuilder = handler.builder().queryParams(queryParams);
        ResponseEntity<DataServiceCoordinate[]> response = this.coordinateRestTemplate.getForEntity(uriBuilder.build(),
                DataServiceCoordinate[].class);
        return response.getBody();
    }

    private MultiValueMap<String, String> buildQueryParam(String geneName, String chromosome, int offset, int pageSize,
                                                          String location) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add(Common.PARAM_GENE, geneName);
        queryParams.add(Common.PARAM_TAXID, Common.TAX_ID_HUMAN);
        if (chromosome != null) {
            queryParams.add(Common.PARAM_CHROMOSOME, chromosome);
        }
        queryParams.add(Common.PARAM_OFFSET, String.valueOf(offset));
        queryParams.add(Common.PARAM_SIZE, String.valueOf(pageSize));
        if (location != null)
            queryParams.add(Common.PARAM_LOCATION, location);
        return queryParams;
    }
}
