package uk.ac.ebi.protvar.converter;

import java.util.*;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.model.data.*;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.uniprot.domain.variation.Variant;

@Service
@AllArgsConstructor
public class GeneConverter {
	private IsoformConverter isoformConverter;

	public List<Gene> createGenes(List<GenomeToProteinMapping> mappings,
								  Set<String> altBases,
								  List<CaddPrediction> caddScores,
								  List<AlleleFreq> alleleFreqs,
								  Map<String, List<Score>>  scoresMap,
								  Map<String, List<Variant>> variantsMap,
								  Map<String, List<Pocket>> pocketsMap,
								  Map<String, List<Interaction>> interactionsMap,
								  Map<String, List<Foldx>> foldxsMap,
								  InputParams params) {
		if (mappings == null || mappings.isEmpty()) return Collections.emptyList();
		return filterEnsgMappings(mappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnsg)))
				.entrySet()
				.stream()
				.flatMap(entry -> {
					String ensg = entry.getKey();
					List<GenomeToProteinMapping> mappingList = entry.getValue();
					GenomeToProteinMapping representative = mappingList.get(0);

					return altBases.stream().map(alt -> {
						var isoforms = isoformConverter.createIsoforms(mappingList, alt,
								scoresMap, variantsMap, pocketsMap, interactionsMap, foldxsMap,
								params);
						return Gene.builder()
								.ensg(ensg)
								.reverseStrand(representative.isReverseStrand())
								.geneName(representative.getGeneName())
								.refAllele(representative.getBaseNucleotide())
								.altAllele(alt)
								.isoforms(isoforms)
								.caddScore(findScore(caddScores, alt))
								.gnomadFreq(findFreq(alleleFreqs, alt))
								.build();
					});
				})
				.collect(Collectors.toList());
	}

	private Double findScore(List<CaddPrediction> scores, String alt) {
		return scores == null ? null :
				scores.stream()
						.filter(s -> alt.equalsIgnoreCase(s.getAltAllele()))
						.map(CaddPrediction::getScore)
						.findFirst()
						.orElse(null);
	}

	private AlleleFreq.GnomadFreq findFreq(List<AlleleFreq> freqs, String alt) {
		return freqs == null ? null :
				freqs.stream()
						.filter(f -> alt.equalsIgnoreCase(f.getAlt()))
						.map(AlleleFreq::toGnomadFreq)
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
