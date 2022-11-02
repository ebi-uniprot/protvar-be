package uk.ac.ebi.protvar.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.model.response.EVEScore;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.response.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.IsoFormMapping;
import uk.ac.ebi.protvar.utils.ReverseCompliment;

@Service
@AllArgsConstructor
public class Mappings2GeneConverter {

	private static final String BASE_U = "U";
	private static final String BASE_T = "T";
	private IsoFormConverter isformConverter;

	public List<Gene> createGenes(List<GenomeToProteinMapping> mappings, String allele, String variantAllele,
								  Double caddScore, Map<String, List<EVEScore>> eveScoreMap, List<OPTIONS> options) {

		Map<String, List<GenomeToProteinMapping>> ensgMappings = mappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnsg));

		Map<String, List<GenomeToProteinMapping>> filteredEnsgMappings  = filterEnsgMappings(ensgMappings);

		return filteredEnsgMappings.keySet().stream().map(ensg -> {

			List<GenomeToProteinMapping> mappingList = filteredEnsgMappings.get(ensg);

			GenomeToProteinMapping genomeToProteinMapping = mappingList.get(0);
			String userAllele = getUserAllele(allele, genomeToProteinMapping.isReverseStrand());

			List<IsoFormMapping> accMappings = isformConverter.createIsoforms(mappingList, userAllele, variantAllele,
					eveScoreMap, options);

			return Gene.builder().ensg(ensg).reverseStrand(genomeToProteinMapping.isReverseStrand())
					.geneName(genomeToProteinMapping.getGeneName())
					.refAllele(genomeToProteinMapping.getBaseNucleotide()).isoforms(accMappings).caddScore(caddScore)
					.build();
		}).collect(Collectors.toList());

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
		String newAllele = refAlleleUser;
		if (reverseStrand)
			newAllele = ReverseCompliment.getCompliment(refAlleleUser);
		newAllele = newAllele.replace(BASE_T, BASE_U);
		return newAllele;
	}

}
