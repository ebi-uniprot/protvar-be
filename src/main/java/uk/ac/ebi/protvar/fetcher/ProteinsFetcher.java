package uk.ac.ebi.protvar.fetcher;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.CacheKey;
import uk.ac.ebi.protvar.converter.UPEntry2FunctionalInfoConverter;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.uniprot.domain.features.Feature;
import uk.ac.ebi.protvar.api.ProteinsAPI;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProteinsFetcher {
	private static final Logger logger = LoggerFactory.getLogger(ProteinsFetcher.class);

	// first option tried
	//private final Map<String, DataServiceProtein> cache = new ConcurrentHashMap<>();
	// second option, guava cache offered automatic eviction when cache reaches specified max
	//private final Cache<String, DataServiceProtein> cache = CacheBuilder.newBuilder().build();
	// third option, disk-based cache (injected as bean)

	private final UPEntry2FunctionalInfoConverter converter;
	private final ProteinsAPI proteinsAPI;
	private final RedisTemplate redisTemplate;


	/**
	 * Prefetch data from Proteins API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessions) {

		Map<Boolean, List<String>> partitioned =
				accessions.stream().collect(
						Collectors.partitioningBy(acc -> redisTemplate.hasKey(CacheKey.protein(acc))));

		Set<String> cached = new HashSet(partitioned.get(true));
		Set<String> notCached = new HashSet(partitioned.get(false));

		logger.info("Cached proteins: {}", String.join(",", cached.toString()));
		logger.info("Not cached proteins: {}", String.join(",", notCached.toString()));

		List<Set<String>> notCachedPartitions = FetcherUtils.partitionSet(notCached, FetcherUtils.PARTITION_SIZE);

		notCachedPartitions.stream().parallel().forEach(accessionsSet -> {
			UPEntry[] entries = proteinsAPI.getProtein(String.join(",", accessionsSet));
			Set<String> newCached = new HashSet<>();
			for (UPEntry entry : entries) {
				redisTemplate.opsForValue().set(CacheKey.protein(entry.getAccession()), entry);
				newCached.add(entry.getAccession());
			}
			logger.info("New cached proteins: {}", String.join(",", newCached.toString()));
		});
	}


	/**
	 * 
	 * @return FunctionalInfo
	 */
	public FunctionalInfo fetch(String accession, int position, String variantAA) {
		if (accession != null && !accession.isBlank()) {
			String key = CacheKey.protein(accession);
			// Try to get from cache
			UPEntry entry = Optional.ofNullable(redisTemplate.opsForValue().get(key))
					.filter(UPEntry.class::isInstance)
					.map(UPEntry.class::cast)
					.orElse(null);

			// If not in cache, fetch from API and cache it
			if (entry == null) {
				UPEntry[] entries = proteinsAPI.getProtein(accession);
				if (entries != null && entries.length > 0) {
					entry = entries[0];
					redisTemplate.opsForValue().set(key, entry);
				}
			}

			if (entry != null) {
				FunctionalInfo functionalInfo = converter.convert(entry);
				functionalInfo.setPosition(position);
				// Filter features based on position
				List<Feature> features = filterFeatures(functionalInfo.getFeatures(), position);
				functionalInfo.setFeatures(features);
				return functionalInfo;
			}
		}
		return null;
	}

	private List<Feature> filterFeatures(List<Feature> features, int position) {
		return features.stream()
				.filter(feature -> isWithinLocationRange(feature, position))
				.collect(Collectors.toList());
	}

	private boolean isWithinLocationRange(Feature feature, int position) {
		try {
			int startPosition = Integer.parseInt(feature.getBegin());
			int endPosition = Integer.parseInt(feature.getEnd());

			// Ensure the position is within the range [startPosition, endPosition]
			return position >= startPosition && position <= endPosition;
		} catch (NumberFormatException e) {
			// Log the issue and return false in case of parsing errors
			//logger.error("Invalid feature range: " + feature.getBegin() + " - " + feature.getEnd());
			return false;
		}
	}

}
