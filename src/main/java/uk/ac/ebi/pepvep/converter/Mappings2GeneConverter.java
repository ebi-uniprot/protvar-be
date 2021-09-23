package uk.ac.ebi.pepvep.converter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.pepvep.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.pepvep.model.response.Gene;
import uk.ac.ebi.pepvep.model.response.GenomeToProteinMapping;
import uk.ac.ebi.pepvep.model.response.IsoFormMapping;
import uk.ac.ebi.pepvep.utils.ReverseCompliment;

@Service
@AllArgsConstructor
public class Mappings2GeneConverter {

	private static final String BASE_U = "U";
	private static final String BASE_T = "T";
	private IsoFormConverter isformConverter;

	public List<Gene> createGenes(List<GenomeToProteinMapping> mappings, String allele, String variantAllele,
			Double caddScore, List<OPTIONS> options) {

		Map<String, List<GenomeToProteinMapping>> ensgMappings = mappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getEnsg));

		return ensgMappings.keySet().stream().map(ensg -> {

			List<GenomeToProteinMapping> mappingList = ensgMappings.get(ensg);

			GenomeToProteinMapping genomeToProteinMapping = mappingList.get(0);
			String userAllele = getUserAllele(allele, genomeToProteinMapping.isReverseStrand());

			List<IsoFormMapping> accMappings = isformConverter.createIsoforms(mappingList, userAllele, variantAllele,
					options);

			return Gene.builder().ensg(ensg).reverseStrand(genomeToProteinMapping.isReverseStrand())
					.geneName(genomeToProteinMapping.getGeneName())
					.refAllele(genomeToProteinMapping.getBaseNucleotide()).isoforms(accMappings).caddScore(caddScore)
					.build();
		}).collect(Collectors.toList());

	}

	private String getUserAllele(String refAlleleUser, boolean reverseStrand) {
		String newAllele = refAlleleUser;
		if (reverseStrand)
			newAllele = ReverseCompliment.getCompliment(refAlleleUser);
		newAllele = newAllele.replace(BASE_T, BASE_U);
		return newAllele;
	}

}
