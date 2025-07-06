package uk.ac.ebi.protvar.converter;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.data.CaddPrediction;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.score.Score;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class GeneConverter {
	private IsoformConverter isoformConverter;

	// Used in MixedInputMapper where ref-alt may be missing;
	// and we default to mappingRef and all alternative bases of that ref
	public List<Gene> createGenes(Set<String> altBases,
								List<GenomeToProteinMapping> mappings,
								List<CaddPrediction> caddScores,
								Map<String, List<Score>>  amScores) {
		if (mappings == null || mappings.isEmpty()) return Collections.emptyList();
		return filterEnsgMappings(mappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnsg)))
				.entrySet()
				.stream()
				.flatMap(entry -> {
					String ensg = entry.getKey();
					List<GenomeToProteinMapping> mappingList = entry.getValue();
					GenomeToProteinMapping representative = mappingList.get(0);

					return altBases.stream().map(altBase -> {
						var isoforms = isoformConverter.createIsoforms(altBase, mappingList, amScores);
						return Gene.builder()
								.ensg(ensg)
								.reverseStrand(representative.isReverseStrand())
								.geneName(representative.getGeneName())
								.refAllele(representative.getBaseNucleotide())
								.altAllele(altBase)
								.isoforms(isoforms)
								.caddScore(findScore(caddScores, altBase))
								.build();
					});
				})
				.collect(Collectors.toList());
	}

	private Double findScore(List<CaddPrediction> scores, String altBase) {
		return scores == null ? null :
				scores.stream()
						.filter(s -> altBase.equalsIgnoreCase(s.getAltAllele()))
						.map(CaddPrediction::getScore)
						.findFirst()
						.orElse(null);
	}

	private Map<String, List<GenomeToProteinMapping>> filterEnsgMappings(Map<String, List<GenomeToProteinMapping>> ensgMappings) {
		if (ensgMappings.size() > 1) {

			// If we have gene group(s) that contains canonical AND mane select, return 1
			for (Map.Entry<String, List<GenomeToProteinMapping>> entry : ensgMappings.entrySet()) {
				String ensg = entry.getKey();
				List<GenomeToProteinMapping> mappings = entry.getValue();
				if (hasCanonicalAndMane(mappings)) {
					return Map.of(ensg, mappings);
				}
			}
			// If we have gene group(s) that contains only canonical, return 1
			for (Map.Entry<String, List<GenomeToProteinMapping>> entry : ensgMappings.entrySet()) {
				String ensg = entry.getKey();
				List<GenomeToProteinMapping> mappings = entry.getValue();
				if (hasCanonical(mappings)) {
					return Map.of(ensg, mappings);
				}
			}

			// If we have gene group(s) that contains only mane select, return 1
			for (Map.Entry<String, List<GenomeToProteinMapping>> entry : ensgMappings.entrySet()) {
				String ensg = entry.getKey();
				List<GenomeToProteinMapping> mappings = entry.getValue();
				if (hasMane(mappings)) {
					return Map.of(ensg, mappings);
				}
			}

			for (Map.Entry<String, List<GenomeToProteinMapping>> entry : ensgMappings.entrySet()) {
				String ensg = entry.getKey();
				return Map.of(ensg, entry.getValue()); // fallback: return the first remaining
			}
		}
		return ensgMappings;
	}

	private Map<String, List<GenomeToProteinMapping>> mapOfFirstEnsg(List<String> ensgs, Map<String, List<GenomeToProteinMapping>> mappings) {
		String ensg = ensgs.get(0);
		return Map.of(ensg, mappings.get(ensg));
	}

	private boolean hasCanonicalAndMane(List<GenomeToProteinMapping> mappings) {
		return mappings.stream().anyMatch(m -> m.isCanonical() && m.isManeSelect());
	}

	private boolean hasCanonical(List<GenomeToProteinMapping> mappings) {
		return mappings.stream().anyMatch(GenomeToProteinMapping::isCanonical);
	}

	private boolean hasMane(List<GenomeToProteinMapping> mappings) {
		return mappings.stream().anyMatch(GenomeToProteinMapping::isManeSelect);
	}

}
