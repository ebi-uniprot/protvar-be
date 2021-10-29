package uk.ac.ebi.protvar.fetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.exception.ServiceException;
import uk.ac.ebi.protvar.model.PDBeRequest;
import uk.ac.ebi.protvar.model.response.PDBeStructure;
import uk.ac.ebi.protvar.repo.UniprotAPIRepo;

@Service
@AllArgsConstructor
public class PDBeFetcher {

	private static final String PDBE_ERROR_MESSAGE = "Call to PDBe API has failed. Some data from the results will be missing.";
	private static final String PD_BE_API_ERROR = "PDBe API error";
	private static final int PDB_REQUEST_SIZE_LIMIT = 10;
	private static final Logger logger = LoggerFactory.getLogger(PDBeFetcher.class);

	private UniprotAPIRepo uniprotApiRepo;

	private Map<String, Object> buildPDBe(List<PDBeRequest> requests) {
		Map<String, Object> map = new HashMap<>();
		logger.info("Calling PDBe {}", requests);
		Object[] dataServicePDBe = uniprotApiRepo.getPDBe(requests);
		for (Object o : dataServicePDBe) {
			Map<String, Map<String, Object>> obj = (Map<String, Map<String, Object>>) o;
			obj.forEach((key, value) -> map.put(key, obj));
		}
		return map;
	}

	private List<List<PDBeRequest>> getPDBeBatchRequests(Map<String, List<Long>> accessionFeatureMap) {
		List<List<PDBeRequest>> requestsList = new ArrayList<>();
		List<PDBeRequest> requests = new ArrayList<>();
		for (Map.Entry<String, List<Long>> entry : accessionFeatureMap.entrySet()) {
			List<String> positions = entry.getValue().stream().map(String::valueOf)
					.collect(Collectors.toList());
			PDBeRequest request = createPDBeRequest(entry.getKey(), positions);
			requests.add(request);
			if (requests.size() == PDB_REQUEST_SIZE_LIMIT) {
				requestsList.add(requests);
				requests = new ArrayList<>();
			}
		}
		if (!requests.isEmpty()) {
			requestsList.add(requests);
		}
		return requestsList;
	}

	private PDBeRequest createPDBeRequest(String accession, List<String> positions) {
		PDBeRequest request = new PDBeRequest();
		request.setAccession(accession);
		request.setPositions(positions);
		return request;
	}

	/**
	 * 
	 * @return - Map of accession and PDB object. Empty map if no data found
	 */
	public Map<String, Object> fetch(Map<String, Long> accessionFeatureMap) throws ServiceException {
		Map<String, Object> accessionPDBeMap = new HashMap<>();
		Map<String, List<Long>> map = new HashMap<>();
		accessionFeatureMap.forEach((accession, position) -> map.put(accession, List.of(position)));
		List<List<PDBeRequest>> requestList = getPDBeBatchRequests(map);
		for (List<PDBeRequest> requests : requestList) {
			try {
				accessionPDBeMap.putAll(buildPDBe(requests));
			} catch (Exception ex) {
				throw new ServiceException(PD_BE_API_ERROR, PDBE_ERROR_MESSAGE);
			}
		}
		return accessionPDBeMap;
	}

	/**
	 * 
	 * @return - Map of accession and PDB object. Empty map if no data found
	 */
	public List<PDBeStructure> fetchByAccession(String accession, int position) {
		return uniprotApiRepo.getPDBeStructure(accession, position);
	}
}
