package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.repo.PopulationRepo;
import uk.ac.ebi.protvar.utils.VariantKey;
import uk.ac.ebi.uniprot.domain.variation.Variant;
import uk.ac.ebi.uniprot.domain.features.Feature;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class VariantFetcher {
	private PopulationRepo populationRepo;

	public Map<String, List<Variant>> getVariants(String accession, int position) {
		List<Feature> features = populationRepo.getFeatures(accession, position);
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
		Map<String, List<Feature>> featureMap = populationRepo.getFeatureMap(accessions, positions);
		return getVariantMap(featureMap);
	}

	public Map<String, List<Variant>> getVariantMap(String accession) {
		Map<String, List<Feature>> featureMap = populationRepo.getFeatureMap(accession);
		return getVariantMap(featureMap);
	}

	public Map<String, List<Variant>> getVariantMap(Map<String, List<Feature>> featureMap) {
		return featureMap.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.filter(Objects::nonNull)
						.filter(f -> f instanceof Variant)
						.map(f -> (Variant) f)
						.collect(Collectors.toList())));
	}
}
