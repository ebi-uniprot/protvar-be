package uk.ac.ebi.pepvep.fetcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import uk.ac.ebi.pepvep.converter.ProteinAPI2ProteinConverter;
import uk.ac.ebi.pepvep.exception.ServiceException;
import uk.ac.ebi.pepvep.model.api.DataServiceProtein;
import uk.ac.ebi.pepvep.model.api.ProteinFeature;
import uk.ac.ebi.pepvep.model.response.Protein;
import uk.ac.ebi.pepvep.repo.UniprotAPIRepo;
import uk.ac.ebi.pepvep.utils.ProteinHelper;

@Service
@AllArgsConstructor
public class ProteinFetcher {
	private static final String PROTEIN_API_ERROR = "Protein API error";
	private static final Logger logger = LoggerFactory.getLogger(ProteinFetcher.class);

	private UniprotAPIRepo uniprotAPIRepo;
	private ProteinAPI2ProteinConverter converter;

	/**
	 * 
	 * @return - Map of accession and Protein. Empty map if no Protein found
	 */
	public Map<String, Protein> fetch(String accessions) throws ServiceException {

		Map<String, Protein> accessionProteinMap = new HashMap<>();
		if (!StringUtils.isEmpty(accessions)) {

			DataServiceProtein[] dataServiceProteins;
			try {
				dataServiceProteins = uniprotAPIRepo.getProtein(accessions);
			} catch (Exception ex) {
				String message = "input:" + accessions + " exception: " + ex.getMessage();
				throw new ServiceException(PROTEIN_API_ERROR, message);
			}
			for (DataServiceProtein dsp : dataServiceProteins) {
				logger.info("Processing accession: {}", dsp.getAccession());
				Protein protein = converter.fetch(dsp);
				accessionProteinMap.put(dsp.getAccession(), protein);
			}
		}
		return accessionProteinMap;
	}

	/**
	 * 
	 * @return - Map of accession and Protein. Empty map if no Protein found
	 */
	public Protein fetch(String accession, int position) {
		if (!StringUtils.isEmpty(accession)) {
			DataServiceProtein[] dataServiceProteins = uniprotAPIRepo.getProtein(accession);
			if (dataServiceProteins != null && dataServiceProteins.length > 0) {
				Protein protein = converter.fetch(dataServiceProteins[0]);
				List<ProteinFeature> features = ProteinHelper.filterFeatures(protein.getFeatures(), position, position);
				protein.setFeatures(features);
				protein.setPosition(position);
				return protein;
			}
		}
		return null;
	}

}
