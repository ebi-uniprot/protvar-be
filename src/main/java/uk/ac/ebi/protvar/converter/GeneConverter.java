package uk.ac.ebi.protvar.converter;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.data.CaddPrediction;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.score.Score;

import java.util.Collections;
import java.util.Comparator;
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
								Map<String, List<Score>>  scoreMap) {
		if (mappings == null || mappings.isEmpty()) return Collections.emptyList();
		// Round B: return ALL overlapping genes (never drop a real mapping). Order so the gene carrying the
		// canonical (then MANE) leads; ensg breaks ties deterministically. (Was a dedup-to-one with a
		// nondeterministic HashMap fallback — canonical/MANE are now ordering signals, not a drop filter.)
		return mappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnsg))
				.entrySet()
				.stream()
				.sorted(Comparator.comparingInt((Map.Entry<String, List<GenomeToProteinMapping>> e) -> geneRank(e.getValue()))
						.thenComparing(Map.Entry::getKey))
				.flatMap(entry -> {
					String ensg = entry.getKey();
					List<GenomeToProteinMapping> mappingList = entry.getValue();
					GenomeToProteinMapping representative = mappingList.get(0);

					return altBases.stream().map(altBase -> {
						var isoforms = isoformConverter.createIsoforms(altBase, mappingList, scoreMap);
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

	/** Gene ordering rank (lower = earlier): canonical+MANE, then canonical, then MANE, then the rest. */
	private int geneRank(List<GenomeToProteinMapping> mappings) {
		if (hasCanonicalAndMane(mappings)) return 0;
		if (hasCanonical(mappings)) return 1;
		if (hasMane(mappings)) return 2;
		return 3;
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
