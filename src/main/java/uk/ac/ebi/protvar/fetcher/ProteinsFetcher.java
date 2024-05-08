package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.ac.ebi.protvar.converter.ProteinsAPI2ProteinConverter;
import uk.ac.ebi.protvar.model.response.Protein;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.utils.ProteinHelper;
import uk.ac.ebi.uniprot.proteins.api.ProteinsAPI;
import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;
import uk.ac.ebi.uniprot.proteins.model.ProteinFeature;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProteinsFetcher {
	private static final Logger logger = LoggerFactory.getLogger(ProteinsFetcher.class);

	private static final String PROT_CACHE_PREFIX = "PROT-";
	// first option tried
	//private final Map<String, DataServiceProtein> dspCache = new ConcurrentHashMap<>();
	// second option, guava cache offered automatic eviction when cache reaches specified max
	//private final Cache<String, DataServiceProtein> dspCache = CacheBuilder.newBuilder().build();
	// third option, disk-based cache (injected as bean)

	private ProteinsAPI2ProteinConverter converter;
	private ProteinsAPI proteinsAPI;

	private ProtVarDataRepo protVarDataRepo;

	private RedisTemplate dspCache;


	/**
	 * Prefetch data from Proteins API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessions) {

		Map<Boolean, List<String>> partitioned =
				accessions.stream().collect(
						Collectors.partitioningBy(acc -> dspCache.hasKey(PROT_CACHE_PREFIX+acc)));

		Set<String> cached = new HashSet(partitioned.get(true));
		Set<String> notCached = new HashSet(partitioned.get(false));

		logger.info("Cached proteins: {}", String.join(",", cached.toString()));
		logger.info("Not cached proteins: {}", String.join(",", notCached.toString()));

		List<Set<String>> notCachedPartitions = FetcherUtils.partitionSet(notCached, FetcherUtils.PARTITION_SIZE);

		notCachedPartitions.stream().parallel().forEach(accessionsSet -> {
			DataServiceProtein[] dataServiceProteins = proteinsAPI.getProtein(String.join(",", accessionsSet));
			Set<String> newCached = new HashSet<>();
			for (DataServiceProtein dsp : dataServiceProteins) {
				dspCache.opsForValue().set(PROT_CACHE_PREFIX+dsp.getAccession(), dsp);
				newCached.add(dsp.getAccession());
			}
			logger.info("New cached proteins: {}", String.join(",", newCached.toString()));
		});
	}


	/**
	 * 
	 * @return Protein
	 */
	public Protein fetch(String accession, int position, String variantAA) {

		if (!StringUtils.isEmpty(accession)) {

			DataServiceProtein dsp = null;
			if (dspCache.hasKey(PROT_CACHE_PREFIX+accession))
				dsp = (DataServiceProtein) dspCache.opsForValue().get(PROT_CACHE_PREFIX+accession);
			if (dsp == null) {
				DataServiceProtein[] dataServiceProteins = proteinsAPI.getProtein(accession);
				if (dataServiceProteins != null && dataServiceProteins.length > 0) {
					dsp = dataServiceProteins[0];
					dspCache.opsForValue().set(PROT_CACHE_PREFIX+accession, dsp);
				}
			}

			if (dsp != null) {
				Protein protein = converter.fetch(dsp);
				List<ProteinFeature> features = ProteinHelper.filterFeatures(protein.getFeatures(), position, position);
				protein.setFeatures(features);
				protein.setPosition(position);
				// add novel predictions
				protein.setPockets(protVarDataRepo.getPockets(accession, position));
				protein.setInteractions(protVarDataRepo.getInteractions(accession, position));
				protein.setFoldxs(protVarDataRepo.getFoldxs(accession, position, variantAA));
				return protein;
			}
		}
		return null;
	}

}
