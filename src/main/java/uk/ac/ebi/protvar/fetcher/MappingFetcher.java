package uk.ac.ebi.protvar.fetcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.model.response.CADDPrediction;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;
import uk.ac.ebi.protvar.model.response.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.repo.VariantsRepository;

@Service
@AllArgsConstructor
public class MappingFetcher {

	private VariantsRepository variantRepository;
	private Mappings2GeneConverter mappingsConverter;

	/**
	 * @return - GenomeProteinMapping - Object containing genomic and protein data
	 *         for a given chromosome and genomicLocation
	 */
	public GenomeProteinMapping getMapping(String chromosome, Long genomicLocation, String id, String allele,
			String variantAllele, List<OPTIONS> options) {
		List<GenomeToProteinMapping> mappings = variantRepository.getMappings(chromosome, genomicLocation);
		if (mappings == null || mappings.isEmpty())
			return GenomeProteinMapping.builder().chromosome(chromosome).geneCoordinateStart(genomicLocation)
					.geneCoordinateEnd(genomicLocation).id(id).userAllele(allele).variantAllele(variantAllele)
					.genes(Collections.emptyList()).build();

		List<CADDPrediction> predictions = variantRepository.getPredictions(List.of(genomicLocation));

		Double caddScore = null;
		if (predictions != null && !predictions.isEmpty())
			caddScore = predictions.get(0).getScore();

		List<Gene> ensgMappingList = mappingsConverter.createGenes(mappings, allele, variantAllele, caddScore, options);

		return GenomeProteinMapping.builder().chromosome(chromosome).geneCoordinateStart(genomicLocation).id(id)
				.geneCoordinateEnd(genomicLocation).userAllele(allele).variantAllele(variantAllele)
				.genes(ensgMappingList).build();
	}

	/**
	 * 
	 * @param inputs is list of String in VCF format
	 * @return - List of GenomeProteinMapping - Object containing genomic and
	 *         protein data for a given chromosome and genomicLocation
	 */
	public MappingResponse getMappings(List<String> inputs, List<OPTIONS> options) {
		MappingResponse response = new MappingResponse();
		List<UserInput> invalidInputs = new ArrayList<>();
		List<UserInput> validInputs = new ArrayList<>();
		response.setInvalidInputs(invalidInputs);
		inputs.stream().map(String::trim)
			.filter(i -> !i.isEmpty())
			.filter(i -> !i.startsWith("#")).forEach(input -> {
			UserInput pInput = UserInput.getInput(input);
			if (pInput.isValid())
				validInputs.add(pInput);
			else
				invalidInputs.add(pInput);
		});

		List<Long> positions = validInputs.stream().map(UserInput::getStart).distinct().collect(Collectors.toList());
		if (!positions.isEmpty()) {

			Map<String, List<CADDPrediction>> predictionMap = variantRepository.getPredictions(positions)
				.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

			Map<String, List<GenomeToProteinMapping>> map = variantRepository.getMappings(positions)
				.stream().collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			List<GenomeProteinMapping> mappingsListToReturn = new ArrayList<>();
			validInputs.forEach(input -> {
				List<GenomeToProteinMapping> mappingList = map.get(input.getGroupBy());
				List<CADDPrediction> caddScores = predictionMap.get(input.getGroupBy());

				Double caddScore = null;
				if (caddScores != null && !caddScores.isEmpty())
					caddScore = getCaddScore(caddScores, input.getAlt());

				List<Gene> ensgMappingList = Collections.emptyList();
				if (mappingList != null)
					ensgMappingList = mappingsConverter.createGenes(mappingList, input.getRef(), input.getAlt(), caddScore, options);
				
				GenomeProteinMapping mapping = GenomeProteinMapping.builder().chromosome(input.getChromosome())
						.geneCoordinateStart(input.getStart()).id(input.getId()).geneCoordinateEnd(input.getStart())
						.userAllele(input.getRef()).variantAllele(input.getAlt()).genes(ensgMappingList).build();
				mapping.setInput(input.getInputString());
				mappingsListToReturn.add(mapping);
			});
			response.setMappings(mappingsListToReturn);
		}
		return response;
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

}
