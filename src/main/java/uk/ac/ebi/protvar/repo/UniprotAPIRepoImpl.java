package uk.ac.ebi.protvar.repo;

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
import uk.ac.ebi.protvar.model.api.DataServiceCoordinate;
import uk.ac.ebi.protvar.model.api.DataServiceProtein;
import uk.ac.ebi.protvar.model.api.DataServiceVariation;

@Repository
//@RequestScope
@AllArgsConstructor
public class UniprotAPIRepoImpl implements UniprotAPIRepo {
	private static final Logger logger = LoggerFactory.getLogger(UniprotAPIRepoImpl.class);
	private static final String PARAM_SIZE = "size";
	private static final String PARAM_OFFSET = "offset";
	private static final String PARAM_GENE = "gene";
	private static final String PARAM_ACCESSION = "accession";
	private static final String TAX_ID_HUMAN = "9606";
	private static final String PARAM_TAXID = "taxid";
	private static final String PARAM_LOCATION = "location";
	private static final String PARAM_CHROMOSOME = "chromosome";

	private RestTemplate variantRestTemplate;
	private RestTemplate proteinRestTemplate;
	private RestTemplate coordinateRestTemplate;

	@Override
	public DataServiceVariation[] getVariationByParam(String paramValue, String pathParam) {
		logger.info("Calling variation: {}", paramValue);
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variantRestTemplate.getUriTemplateHandler();
		UriBuilder uriBuilder = handler.builder().path(pathParam).path(paramValue);

		ResponseEntity<DataServiceVariation[]> response = this.variantRestTemplate.getForEntity(uriBuilder.build(),
				DataServiceVariation[].class);
		return response.getBody();
	}

	@Override
	public DataServiceVariation[] getVariationByAccession(String accession, String location) {
		logger.info("Calling colocated variation: {}", accession);
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.variantRestTemplate.getUriTemplateHandler();
		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add(PARAM_TAXID, TAX_ID_HUMAN);
		queryParams.add(PARAM_ACCESSION, accession);
		if (location != null) {
			queryParams.add(PARAM_LOCATION, location);
		}
		UriBuilder uriBuilder = handler.builder().queryParams(queryParams);
		ResponseEntity<DataServiceVariation[]> response = this.variantRestTemplate.getForEntity(uriBuilder.build(),
				DataServiceVariation[].class);
		return response.getBody();
	}

	@Override
	public DataServiceProtein[] getProtein(String accessions) {
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.proteinRestTemplate.getUriTemplateHandler();
		logger.info("Calling protein for accessions -> {}", accessions);
		UriBuilder uriBuilder = handler.builder().queryParam(PARAM_ACCESSION, accessions).queryParam(PARAM_TAXID,
				TAX_ID_HUMAN);
		ResponseEntity<DataServiceProtein[]> response = this.proteinRestTemplate.getForEntity(uriBuilder.build(),
				DataServiceProtein[].class);
		return response.getBody();
	}
/*
	@Override
	public DataServiceCoordinate[] getGene(UserInput userInput) {
		logger.info("Calling gene: {}", userInput);
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.coordinateRestTemplate
				.getUriTemplateHandler();
		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add(PARAM_CHROMOSOME, userInput.getChr());
		String location = userInput.getStart() + "-" + userInput.getStart();
		queryParams.add(PARAM_LOCATION, location);
		queryParams.add(PARAM_TAXID, TAX_ID_HUMAN);

		UriBuilder uriBuilder = handler.builder().queryParams(queryParams);
		ResponseEntity<DataServiceCoordinate[]> response = this.coordinateRestTemplate.getForEntity(uriBuilder.build(),
				DataServiceCoordinate[].class);
		return response.getBody();
	}*/

	@Override
	public DataServiceCoordinate[] getCoordinateByAccession(String accession) {
		logger.info("Calling gene: {}", accession);
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.coordinateRestTemplate
				.getUriTemplateHandler();
		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add(PARAM_ACCESSION, accession);
		queryParams.add(PARAM_TAXID, TAX_ID_HUMAN);
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
		queryParams.add(PARAM_GENE, geneName);
		queryParams.add(PARAM_TAXID, TAX_ID_HUMAN);
		if (chromosome != null) {
			queryParams.add(PARAM_CHROMOSOME, chromosome);
		}
		queryParams.add(PARAM_OFFSET, String.valueOf(offset));
		queryParams.add(PARAM_SIZE, String.valueOf(pageSize));
		if (location != null)
			queryParams.add(PARAM_LOCATION, location);
		return queryParams;
	}

}
