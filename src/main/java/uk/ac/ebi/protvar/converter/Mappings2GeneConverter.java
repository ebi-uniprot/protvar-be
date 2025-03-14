package uk.ac.ebi.protvar.converter;

import java.util.*;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.protvar.model.data.CADDPrediction;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.IsoFormMapping;
import uk.ac.ebi.protvar.model.response.Variation;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.utils.ReverseCompliment;

@Service
@AllArgsConstructor
public class Mappings2GeneConverter {

	private static final String BASE_U = "U";
	private static final String BASE_T = "T";
	private IsoFormConverter isformConverter;

	public List<Gene> createGenes(List<GenomeToProteinMapping> mappings,
								  GenomicInput gInput,
								  Set<String> altBases,
								  List<CADDPrediction> caddScores,
								  List<AlleleFreq> alleleFreqs,
								  Map<String, List<Score>>  scoreMap,
								  Map<String, List<Variation>> variationMap, InputParams params) {

		List<Gene> ensgMappingList = new ArrayList<>();
		if (mappings == null)
			return ensgMappingList;

		Map<String, List<GenomeToProteinMapping>> ensgMappings = mappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnsg));

		Map<String, List<GenomeToProteinMapping>> filteredEnsgMappings  = filterEnsgMappings(ensgMappings);

		filteredEnsgMappings.keySet().forEach(ensg -> {

			List<GenomeToProteinMapping> mappingList = filteredEnsgMappings.get(ensg);

			GenomeToProteinMapping genomeToProteinMapping = mappingList.get(0);
			//String userAllele = getUserAllele(gInput.getRef(), genomeToProteinMapping.isReverseStrand());

			altBases.forEach(alt -> {

				List<IsoFormMapping> isoforms = isformConverter.createIsoforms(mappingList, /*userAllele,*/ alt,
						scoreMap, variationMap, params);

				ensgMappingList.add(Gene.builder().ensg(ensg).reverseStrand(genomeToProteinMapping.isReverseStrand())
						.geneName(genomeToProteinMapping.getGeneName())
						.refAllele(genomeToProteinMapping.getBaseNucleotide())
						.altAllele(alt)
						.isoforms(isoforms)
						.caddScore(getCaddScore(caddScores, alt))
						.alleleFreq(getAlleleFreq(alleleFreqs, alt))
						.build());
			});
		});
		return ensgMappingList;
	}

	private Double getCaddScore(List<CADDPrediction> caddScores, String alt) {
		if (caddScores != null && !caddScores.isEmpty()) {
			Optional<CADDPrediction> prediction = caddScores.stream()
					.filter(p -> alt.equalsIgnoreCase(p.getAltAllele())).findAny();
			if (prediction.isPresent())
				return prediction.get().getScore();
			return null;
		}
		return null;
	}

	private Double getAlleleFreq(List<AlleleFreq> alleleFreqs, String alt) {
		if (alleleFreqs != null && !alleleFreqs.isEmpty()) {
			Optional<AlleleFreq> freq = alleleFreqs.stream()
					.filter(p -> alt.equalsIgnoreCase(p.getAlt())).findAny();
			if (freq.isPresent())
				return freq.get().getAf();
			return null;
		}
		return null;
	}

	private Map<String, List<GenomeToProteinMapping>> filterEnsgMappings(Map<String, List<GenomeToProteinMapping>> ensgMappings) {
		List<String> hasCanonicalAndManeSelect = new ArrayList<>();
		List<String> hasCanonicalOnly = new ArrayList<>();
		List<String> hasManeSelectOnly = new ArrayList<>();
		List<String> hasNone = new ArrayList<>();

		if (ensgMappings.size() > 1) {
			// get each gene group one by one
			for (String ensg : ensgMappings.keySet()) {
				List<GenomeToProteinMapping> mappings = ensgMappings.get(ensg);
				if (containsCanonicalAndManeSelect(mappings)) {
					hasCanonicalAndManeSelect.add(ensg);
				} else if (containsCanonicalOnly(mappings)) {
					hasCanonicalOnly.add(ensg);
				} else if (containsManeSelectOnly(mappings)) {
					hasManeSelectOnly.add(ensg);
				} else {
					hasNone.add(ensg);
				}
			}
			// 1. if we have gene group(s) that contains canonical AND mane select, return 1
			if (!hasCanonicalAndManeSelect.isEmpty()) {
				return mapOfFirstEnsg(hasCanonicalAndManeSelect, ensgMappings);
			}
			// 2. if we have gene group(s) that contains only canonical, return 1
			if (!hasCanonicalOnly.isEmpty()) {
				return mapOfFirstEnsg(hasCanonicalOnly, ensgMappings);
			}
			// 3. if we have gene group(s) that contains only mane select, return 1
			if (!hasManeSelectOnly.isEmpty()) {
				return mapOfFirstEnsg(hasManeSelectOnly, ensgMappings);
			}
			// 4. else if we have gene group(s) that contains none, return 1
			if (!hasNone.isEmpty()) {
				return mapOfFirstEnsg(hasNone, ensgMappings);
			}
		}
		return ensgMappings;
	}

	private Map<String, List<GenomeToProteinMapping>> mapOfFirstEnsg(List<String> ensgs, Map<String, List<GenomeToProteinMapping>> mappings) {
		String ensg = ensgs.get(0);
		return Map.of(ensg, mappings.get(ensg));
	}

	private boolean containsCanonicalAndManeSelect(List<GenomeToProteinMapping> mappings) {
		for (GenomeToProteinMapping mapping : mappings) {
			if (mapping.isCanonical() && mapping.isManeSelect()) {
				return true;
			}
		}
		return false;
	}

	private boolean containsCanonicalOnly(List<GenomeToProteinMapping> mappings) {
		for (GenomeToProteinMapping mapping : mappings) {
			if (mapping.isCanonical()) {
				return true;
			}
		}
		return false;
	}

	private boolean containsManeSelectOnly(List<GenomeToProteinMapping> mappings) {
		for (GenomeToProteinMapping mapping : mappings) {
			if (mapping.isManeSelect()) {
				return true;
			}
		}
		return false;
	}

	private String getUserAllele(String refAlleleUser, boolean reverseStrand) {
		if (refAlleleUser == null) {
			return null;
		}

		String newAllele = refAlleleUser;
		if (reverseStrand) {
			newAllele = ReverseCompliment.getCompliment(refAlleleUser);
			if (newAllele == null) {
				return null; // Or handle the case as needed
			}
		}

		newAllele = newAllele.replace(BASE_T, BASE_U);
		return newAllele;
	}
}
