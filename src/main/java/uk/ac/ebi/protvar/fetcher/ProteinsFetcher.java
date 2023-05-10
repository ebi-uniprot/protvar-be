package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.ac.ebi.protvar.converter.ProteinsAPI2ProteinConverter;
import uk.ac.ebi.protvar.model.response.Protein;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.utils.ProteinHelper;
import uk.ac.ebi.uniprot.proteins.api.ProteinsAPI;
import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;
import uk.ac.ebi.uniprot.proteins.model.ProteinFeature;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProteinsFetcher {
	private static final String PROTEIN_API_ERROR = "Protein API error";
	private static final Logger logger = LoggerFactory.getLogger(ProteinsFetcher.class);
	// first option tried
	//private final Map<String, DataServiceProtein> dspCache = new ConcurrentHashMap<>();
	// second option, guava cache offered automatic eviction when cache reaches specified max
	//private final Cache<String, DataServiceProtein> dspCache = CacheBuilder.newBuilder().build();
	// third option, disk-based cache (injected as bean)

	private ProteinsAPI2ProteinConverter converter;
	private ProteinsAPI proteinsAPI;

	private HTreeMap<String, DataServiceProtein> dspCache;


	/**
	 * Prefetch data from Proteins API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessions) {
		Set<String> cachedAccessions = dspCache./*asMap().*/keySet();

		// check accession in ProteinsCache
		Set<String> notCachedAccessions = accessions.stream().filter(Predicate.not(cachedAccessions::contains)).collect(Collectors.toSet());
		List<Set<String>> notCachedAccessionsPartitions = FetcherUtils.partitionSet(notCachedAccessions, FetcherUtils.PARTITION_SIZE);

		notCachedAccessionsPartitions.stream().parallel().forEach(accessionsSet -> {
			DataServiceProtein[] dataServiceProteins = proteinsAPI.getProtein(String.join(",", accessionsSet));
			Map<String, DataServiceProtein> proteinsMap = new ConcurrentHashMap<>();
			for (DataServiceProtein dsp : dataServiceProteins) {
				proteinsMap.put(dsp.getAccession(), dsp);
			}
			logger.info("Caching Protein: {}", String.join(",", proteinsMap.keySet()));
			dspCache.putAll(proteinsMap);
		});
	}


	/**
	 * 
	 * @return Protein
	 */
	public Protein fetch(String accession, int position) {

		if (!StringUtils.isEmpty(accession)) {

			DataServiceProtein dsp = null;
			if (dspCache.containsKey(accession))
				dsp = dspCache.get(accession);
			if (dsp == null) {
				DataServiceProtein[] dataServiceProteins = proteinsAPI.getProtein(accession);
				if (dataServiceProteins != null && dataServiceProteins.length > 0) {
					dsp = dataServiceProteins[0];
					dspCache.put(accession, dsp);
				}
			}

			if (dsp != null) {
				Protein protein = converter.fetch(dsp);
				List<ProteinFeature> features = ProteinHelper.filterFeatures(protein.getFeatures(), position, position);
				protein.setFeatures(features);
				protein.setPosition(position);
				return protein;
			}
		}
		return null;
	}

}
