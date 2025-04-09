package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.CacheKey;
import uk.ac.ebi.protvar.repo.VariationRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.uniprot.domain.variation.Variant;
import uk.ac.ebi.protvar.service.VariationAPI;
import uk.ac.ebi.uniprot.domain.features.ProteinFeatureInfo;
import uk.ac.ebi.uniprot.domain.features.Feature;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static uk.ac.ebi.protvar.utils.Commons.notNullNotEmpty;

@Service
@AllArgsConstructor
public class VariantFetcher {
	private static final Logger logger = LoggerFactory.getLogger(VariantFetcher.class);
	private VariationAPI variationAPI; // from API
	private VariationRepo variationRepo; // from Repo i.e. ProtVar DB tbl

	private RedisTemplate redisTemplate;

	/**
	 * Prefetch data from Variation API and cache in application for
	 * subsequent retrieval.
	 */
	public void prefetch(Set<String> accessionLocations) {
		Map<Boolean, List<String>> partitioned =
				accessionLocations.stream().collect(
						Collectors.partitioningBy(accLoc -> redisTemplate.hasKey(CacheKey.variant(accLoc))));

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
		Map<String, List<Variant>> variantMap = new ConcurrentHashMap<>();
		for (String k: accessionLocations) {
			variantMap.put(k, new ArrayList<>());
		}

		try {
			ProteinFeatureInfo[] proteinFeatureInfos = variationAPI.getVariationAccessionLocations(String.join("|", accessionLocations));
			if (proteinFeatureInfos != null && proteinFeatureInfos.length > 0) {

				for (ProteinFeatureInfo proteinFeatureInfo : proteinFeatureInfos) {
					proteinFeatureInfo.getFeatures().stream()
							.filter(Objects::nonNull)
							.filter(f -> f instanceof Variant)
							.filter(f -> f.getBegin().equals(f.getEnd())) // single nucleotide variant only
							.map(f -> (Variant) f)
							.filter(v -> notNullNotEmpty(v.getAlternativeSequence()))
							.filter(v -> notNullNotEmpty(v.getWildType()))
							.forEach(v -> {
								String key = proteinFeatureInfo.getAccession() + ":" + v.getBegin();
								if (variantMap.containsKey(key)) {
									variantMap.get(key).add(v);
								}
							});
				}
				logger.info("Caching variation: {}", String.join(",", accessionLocations));
				// update cache
				for (String key : variantMap.keySet()) {
					redisTemplate.opsForValue().set(CacheKey.variant(key), variantMap.get(key));
				}
			}
		}
		catch (Exception ex) {
			logger.error(ex.getMessage());
		}
	}

	public List<Variant> fetch(String uniprotAccession, int proteinLocation) {
		String accLoc = uniprotAccession + ":" + proteinLocation;
		String key = CacheKey.variant(accLoc);
		List<Variant> variants = getCachedVariants(key);
		if (variants != null) return variants;

		cacheAPIResponse(Set.of(accLoc));

		variants = getCachedVariants(key);
		return (variants != null) ? variants : Collections.emptyList();
	}

	public List<Variant> getVariants(String accession, int protein) {
		List<Feature> features = variationRepo.getFeatures(accession, protein);
		List<Variant> variants = features.stream()
				.filter(Objects::nonNull)
				.filter(f -> f instanceof Variant)
				.map(f -> (Variant) f)
				.collect(Collectors.toList());
		return variants;
	}

	public Map<String, List<Variant>> getVariantMap(List<Object[]> accPosList) {
		Map<String, List<Feature>> featureMap = variationRepo.getFeatureMap(accPosList);
		Map<String, List<Variant>> variantMap = featureMap.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.filter(Objects::nonNull)
						.filter(f -> f instanceof Variant)
						.map(f -> (Variant) f)
						.collect(Collectors.toList())));
		return variantMap;
	}

	@SuppressWarnings("unchecked")
	private List<Variant> getCachedVariants(String key) {
		Object value = redisTemplate.opsForValue().get(key);
		if (value instanceof List<?>) {
			List<?> list = (List<?>) value;
			if (list.isEmpty() || list.get(0) instanceof Variant) {
				return (List<Variant>) list;
			}
		}
		return null;
	}

}
