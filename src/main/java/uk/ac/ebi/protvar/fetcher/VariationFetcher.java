package uk.ac.ebi.protvar.fetcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.function.Predicate;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.converter.VariationAPI2VariationConverter;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.uniprot.variation.api.VariationAPI;
import uk.ac.ebi.uniprot.variation.model.DataServiceVariation;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.response.Variation;
import uk.ac.ebi.uniprot.variation.model.Feature;

import static uk.ac.ebi.protvar.utils.Commons.notNullNotEmpty;

@Service
@AllArgsConstructor
public class VariationFetcher {
	private static final Logger logger = LoggerFactory.getLogger(VariationFetcher.class);
	private final Cache<String, List<Variation>> cache = CacheBuilder.newBuilder().build();

	private VariationAPI2VariationConverter converter;
	private VariationAPI variationAPI;

	/**
	 * Prefetch data from Variation API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessionLocations) {
		Set<String> cached = cache.asMap().keySet();

		// check accession-location in variation cache
		Set<String> notCached = accessionLocations.stream().filter(Predicate.not(cached::contains)).collect(Collectors.toSet());
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
				logger.info("Caching Variation: {}", String.join(",", accessionLocations));
				// update cache
				cache.putAll(variationMap);
			}
		}
		catch (Exception ex) {
			logger.error(ex.getMessage());
		}
	}

	public List<Variation> fetch(String uniprotAccession, int proteinLocation) {
		String key = uniprotAccession + ":" + proteinLocation;
		List<Variation> variations = cache.getIfPresent(key);
		if (variations != null)
			return variations;

		cacheAPIResponse(new HashSet<>(Arrays.asList(key)));

		variations = cache.getIfPresent(key);
		if (variations != null)
			return variations;

		return Collections.emptyList();
	}

	private boolean isWithinLocationRange(long begin, long end, Feature feature) {
		return (begin >= feature.getBegin() && end <= feature.getEnd())
				|| (begin >= feature.getBegin() && begin <= feature.getEnd())
				|| (end >= feature.getBegin() && end <= feature.getEnd());
	}

	public PopulationObservation fetchPopulationObservation(String accession, int proteinLocation) {
		List<Variation> variations = fetch(accession, proteinLocation);

		PopulationObservation populationObservation = new PopulationObservation();
		populationObservation.setProteinColocatedVariant(variations);
		return populationObservation;
	}

}
