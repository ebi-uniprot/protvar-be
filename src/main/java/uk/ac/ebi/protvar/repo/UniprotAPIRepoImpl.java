package uk.ac.ebi.protvar.repo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import uk.ac.ebi.protvar.model.PDBeRequest;
import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.model.api.DataServiceCoordinate;
import uk.ac.ebi.protvar.model.api.DataServiceProtein;
import uk.ac.ebi.protvar.model.api.DataServiceVariation;
import uk.ac.ebi.protvar.model.response.PDBeStructure;

@Repository
@RequestScope
@AllArgsConstructor
public class UniprotAPIRepoImpl implements UniprotAPIRepo {
	private static final Logger logger = LoggerFactory.getLogger(UniprotAPIRepoImpl.class);

	private static final String API_MAPPINGS_UNIPROT = "api/mappings/uniprot/";
	private static final String GRAPH_API_SUMMARY_STATS = "graph-api/protvar/summary_stats";
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
	private RestTemplate pdbeRestTemplate;

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

	@Override
	public DataServiceCoordinate[] getGene(UserInput userInput) {
		logger.info("Calling gene: {}", userInput);
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.coordinateRestTemplate
				.getUriTemplateHandler();
		MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		queryParams.add(PARAM_CHROMOSOME, userInput.getChromosome());
		String location = userInput.getStart() + "-" + userInput.getStart();
		queryParams.add(PARAM_LOCATION, location);
		queryParams.add(PARAM_TAXID, TAX_ID_HUMAN);

		UriBuilder uriBuilder = handler.builder().queryParams(queryParams);
		ResponseEntity<DataServiceCoordinate[]> response = this.coordinateRestTemplate.getForEntity(uriBuilder.build(),
				DataServiceCoordinate[].class);
		return response.getBody();
	}

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
	public Object[] getPDBe(List<PDBeRequest> requests) {
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.pdbeRestTemplate.getUriTemplateHandler();
		UriBuilder uriBuilder = handler.builder().path(GRAPH_API_SUMMARY_STATS);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<List<PDBeRequest>> requestEntity = new HttpEntity<>(requests, headers);
		ResponseEntity<Object[]> response = this.pdbeRestTemplate.postForEntity(uriBuilder.build(), requestEntity,
				Object[].class);

		return response.getBody();

	}

	@Override
	public List<PDBeStructure> getPDBeStructure(String accession, int aaPosition) {
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.pdbeRestTemplate.getUriTemplateHandler();
		UriBuilder uriBuilder = handler.builder().path(accession).path("/").path(String.valueOf(aaPosition)).path("/")
				.path(String.valueOf(++aaPosition));
		List<PDBeStructure> structures = new ArrayList<>();

		try {
			ResponseEntity<Object> response = this.pdbeRestTemplate.getForEntity(uriBuilder.build(), Object.class);
			if (response.getBody() != null && response.getBody() instanceof Map) {
				Map<?,?> structure = (Map<?,?>) response.getBody();
				List<Map> accStr = (List<Map>) structure.get(accession);
				accStr.forEach(str -> {
					PDBeStructure pdbeStr = new PDBeStructure();
					pdbeStr.setChain_id(str.get("chain_id").toString());
					pdbeStr.setExperimental_method(str.get("experimental_method").toString());
					pdbeStr.setPdb_id(str.get("pdb_id").toString());
					pdbeStr.setResolution(Float.parseFloat(str.get("resolution").toString()));
					pdbeStr.setStart(Integer.parseInt(str.get("start").toString()));
					structures.add(pdbeStr);
				});
				
			}
			return structures;
		} catch (Exception ex) {
			logger.error("Exception calling pdb API for accession {}, Position {}", accession, aaPosition);
			return null;
		}

	}

	@Override
	public String getUniproAccession(String accession) {
		DefaultUriBuilderFactory handler = (DefaultUriBuilderFactory) this.pdbeRestTemplate.getUriTemplateHandler();
		UriBuilder uriBuilder = handler.builder().path(API_MAPPINGS_UNIPROT).path(accession);
		ResponseEntity<Object> response = this.pdbeRestTemplate.getForEntity(uriBuilder.build(), Object.class);
		if (response != null && response.getBody() != null) {
			Map<String, Map> responseBody = (Map<String, Map>) response.getBody();
			Map<String, Map> accessionMap = responseBody.get(accession);
			Map<String, Map> uniProtMap = accessionMap.get("UniProt");
			String uniProtAccession = (String) uniProtMap.keySet().toArray()[0];
			return uniProtAccession;
		}
		return null;
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
