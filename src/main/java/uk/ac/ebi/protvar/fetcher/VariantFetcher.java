package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.CacheKey;
import uk.ac.ebi.protvar.repo.VariationRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.utils.VariantKey;
import uk.ac.ebi.uniprot.domain.variation.Variant;
import uk.ac.ebi.protvar.api.VariationAPI;
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

		List<Collection<String>> notCachedPartitions = FetcherUtils.partition(notCached, VariationAPI.PARTITION_SIZE);

		notCachedPartitions.stream().parallel().forEach(accessionsSet -> {
			cacheAPIResponse(accessionsSet);
		});
	}

	private void cacheAPIResponse(Collection<String> accessionPositions) {
		Map<String, List<Variant>> variantMap = new ConcurrentHashMap<>();
		for (String k: accessionPositions) {
			variantMap.put(k, new ArrayList<>());
		}

		try {
			ProteinFeatureInfo[] proteinFeatureInfos = variationAPI.getVariationAccessionLocations(String.join("|", accessionPositions));
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
								String variantKey = VariantKey.protein(proteinFeatureInfo.getAccession(), v.getBegin());
								if (variantMap.containsKey(variantKey)) {
									variantMap.get(variantKey).add(v);
								}
							});
				}
				logger.info("Caching variation: {}", String.join(",", accessionPositions));
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
		String variantKey = VariantKey.protein(uniprotAccession, proteinLocation);
		String cacheKey = CacheKey.variant(variantKey);
		List<Variant> variants = getCachedVariants(cacheKey);
		if (variants != null) return variants;

		cacheAPIResponse(Set.of(variantKey));

		variants = getCachedVariants(cacheKey);
		return (variants != null) ? variants : Collections.emptyList();
	}

	public Map<String, List<Variant>> getVariants(String accession, int position) {
		List<Feature> features = variationRepo.getFeatures(accession, position);
		List<Variant> variants = features.stream()
				.filter(Objects::nonNull)
				.filter(f -> f instanceof Variant)
				.map(f -> (Variant) f)
				.collect(Collectors.toList());
		if (variants == null || variants.isEmpty())
			return Map.of();
		return Map.of(VariantKey.protein(accession, position), variants);
	}

	public Map<String, List<Variant>> getVariantMap(String[] accessions, Integer[] positions) {
		Map<String, List<Feature>> featureMap = variationRepo.getFeatureMap(accessions, positions);
		return getVariantMap(featureMap);
	}

	public Map<String, List<Variant>> getVariantMap(String accession) {
		Map<String, List<Feature>> featureMap = variationRepo.getFeatureMap(accession);
		return getVariantMap(featureMap);
	}

	public Map<String, List<Variant>> getVariantMap(Map<String, List<Feature>> featureMap) {
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
