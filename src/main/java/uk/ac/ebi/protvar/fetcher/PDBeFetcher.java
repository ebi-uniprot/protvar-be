package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.pdbe.api.PDBeAPI;
import uk.ac.ebi.pdbe.cache.PDBeCache;
import uk.ac.ebi.pdbe.model.PDBeStructureResidue;

import java.util.List;

@Service
@AllArgsConstructor
public class PDBeFetcher {
	private static final Logger logger = LoggerFactory.getLogger(PDBeFetcher.class);
	private static final boolean CACHE = true;
	private PDBeAPI pdbeAPI;
	private PDBeCache pdbeCache;


	/**
	 * 
	 * @return - Map of accession and PDB object. Empty map if no data found
	 */
	public List<PDBeStructureResidue> fetch(String accession, int position) {
		if (CACHE) {
			//logger.info("Fetching from PDBe cache");
			return pdbeCache.get(accession, position);
		}
		return pdbeAPI.get(accession, position);
	}
}
