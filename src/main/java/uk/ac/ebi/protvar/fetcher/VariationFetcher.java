package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.VariationCache;
import uk.ac.ebi.protvar.converter.VariationAPI2VariationConverter;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.response.Variation;
import uk.ac.ebi.protvar.repo.VariationRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.uniprot.variation.api.VariationAPI;
import uk.ac.ebi.uniprot.variation.model.DataServiceVariation;
import uk.ac.ebi.uniprot.variation.model.Feature;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static uk.ac.ebi.protvar.utils.Commons.notNullNotEmpty;

@Service
@AllArgsConstructor
public class VariationFetcher {
	private static final Logger logger = LoggerFactory.getLogger(VariationFetcher.class);
	private VariationAPI2VariationConverter converter;
	private VariationAPI variationAPI; // from API
	private VariationRepo variationRepo; // from Repo i.e. ProtVar DB tbl

	private RedisTemplate variationCache;

	/**
	 * Prefetch data from Variation API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessionLocations) {
		Map<Boolean, List<String>> partitioned =
				accessionLocations.stream().collect(
						Collectors.partitioningBy(accLoc -> variationCache.hasKey(VariationCache.keyOf(accLoc))));

		Set<String> cached = new HashSet(partitioned.get(true));
		Set<String> notCached = new HashSet(partitioned.get(false));

		logger.info("Cached variation: {}", String.join(",", cached.toString()));
		logger.info("Not cached: {}", String.join(",", notCached.toString()));

		List<Set<String>> notCachedPartitions = FetcherUtils.partitionSet(notCached, FetcherUtils.PARTITION_SIZE);

		notCachedPartitions.stream().parallel().forEach(accessionsSet -> {
			cacheAPIResponse(accessionsSet);
		});
	}

	private void cacheAPIResponse(Set<String> accessionLocations) {
		Map<String, List<Variation>> variationMap = new ConcurrentHashMap<>();
		for (String k: accessionLocations) {
			variationMap.put(k, new ArrayList<>());
		}

		try {
			DataServiceVariation[] dataServiceVariations = variationAPI.getVariationAccessionLocations(String.join("|", accessionLocations));
			if (dataServiceVariations != null && dataServiceVariations.length > 0) {

				for (DataServiceVariation dsv : dataServiceVariations) {
					dsv.getFeatures().stream()
							.filter(Objects::nonNull)
							.filter(f -> f.getBegin() == f.getEnd()) // single nucleotide variant only
							.map(converter::convert)
							.filter(v -> notNullNotEmpty(v.getAlternativeSequence()))
							.filter(v -> notNullNotEmpty(v.getWildType()))
							.forEach(v -> {
								String key = dsv.getAccession() + ":" + v.getBegin();
								if (variationMap.containsKey(key)) {
									variationMap.get(key).add(v);
								}
							});
				}
				logger.info("Caching variation: {}", String.join(",", accessionLocations));
				// update cache
				for (String key : variationMap.keySet()) {
					variationCache.opsForValue().set(VariationCache.keyOf(key), variationMap.get(key));
				}
			}
		}
		catch (Exception ex) {
			logger.error(ex.getMessage());
		}
	}

	public List<Variation> fetch(String uniprotAccession, int proteinLocation) {
		String accLoc = uniprotAccession + ":" + proteinLocation;
		String key = VariationCache.keyOf(accLoc);
		if (variationCache.hasKey(key))
			return (List<Variation>) variationCache.opsForValue().get(key);

		cacheAPIResponse(new HashSet<>(Arrays.asList(accLoc)));

		if (variationCache.hasKey(key))
			return (List<Variation>) variationCache.opsForValue().get(key);

		return Collections.emptyList();
	}

	private boolean isWithinLocationRange(long begin, long end, Feature feature) {
		return (begin >= feature.getBegin() && end <= feature.getEnd())
				|| (begin >= feature.getBegin() && begin <= feature.getEnd())
				|| (end >= feature.getBegin() && end <= feature.getEnd());
	}

	public PopulationObservation fetchPopulationObservation(String accession, int proteinLocation) {
		//List<Variation> variations = fetch(accession, proteinLocation);
		List<Variation> variations = fetchdb(accession, proteinLocation);
		PopulationObservation populationObservation = new PopulationObservation();
		populationObservation.setProteinColocatedVariant(variations);
		return populationObservation;
	}

	public Map<String, List<Variation>> prefetchdb(Set<Object[]> accPosSet) {
		Map<String, List<Feature>> featureMap = variationRepo.getFeatureMap(accPosSet);
		Map<String, List<Variation>> varMap = featureMap.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.filter(Objects::nonNull)
						.map(converter::convert)
						.collect(Collectors.toList())));
		return varMap;
	}

	private List<Variation> fetchdb(String accession, int proteinLocation) {
		List<Feature> features = variationRepo.getFeatures(accession, proteinLocation);
		return features.stream()
				.filter(Objects::nonNull)
				.map(converter::convert)
				.collect(Collectors.toList());
	}

}
