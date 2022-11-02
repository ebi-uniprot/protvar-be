package uk.ac.ebi.protvar.fetcher;

import java.util.*;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.VariantsRepository;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.RNACodon;

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

		List<Gene> ensgMappingList = mappingsConverter.createGenes(mappings, allele, variantAllele, caddScore, null, options);

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
					if (pInput.isValid()) {
						if (pInput.getType() == UserInput.Type.PROTAC) {
							addNewInputsUsingGenomicFromProteinInfo(validInputs, invalidInputs, pInput);
						} else {
							validInputs.add(pInput);
						}
					}
					else
						invalidInputs.add(pInput);
				});

		List<Long> positions = validInputs.stream().map(UserInput::getStart).distinct().collect(Collectors.toList());
		if (!positions.isEmpty()) {

			Map<String, List<CADDPrediction>> predictionMap = variantRepository.getPredictions(positions)
				.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

			List<GenomeToProteinMapping> g2pMappings = variantRepository.getMappings(positions);
			List <String> proteinAccessions = new ArrayList<>();
			List <Integer> proteinPositions = new ArrayList<>();
			g2pMappings.forEach(m -> {
				proteinAccessions.add(m.getAccession());
				proteinPositions.add(m.getIsoformPosition());
			});
			Map<String, List<EVEScore>> eveScoreMap = variantRepository.getEVEScores(proteinAccessions, proteinPositions)
					.stream().collect(Collectors.groupingBy(EVEScore::getGroupBy));

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			List<GenomeProteinMapping> mappingsListToReturn = new ArrayList<>();
			validInputs.forEach(input -> {
				List<GenomeToProteinMapping> mappingList = map.get(input.getGroupBy());
				List<CADDPrediction> caddScores = predictionMap.get(input.getGroupBy());

				Double caddScore = null;
				if (caddScores != null && !caddScores.isEmpty())
					caddScore = getCaddScore(caddScores, input.getAlt());

				List<Gene> ensgMappingList = Collections.emptyList();
				if (mappingList != null)
					ensgMappingList = mappingsConverter.createGenes(mappingList, input.getRef(), input.getAlt(), caddScore, eveScoreMap, options);
				
				GenomeProteinMapping mapping = GenomeProteinMapping.builder().chromosome(input.getChromosome())
						.geneCoordinateStart(input.getStart()).id(input.getId()).geneCoordinateEnd(input.getStart())
						.userAllele(input.getRef()).variantAllele(input.getAlt()).genes(ensgMappingList)
						.input(input.getFormattedInputString()).build();
				mappingsListToReturn.add(mapping);
			});
			response.setMappings(mappingsListToReturn);
		}
		return response;
	}

	private void addNewInputsUsingGenomicFromProteinInfo(List<UserInput> validInputs, List<UserInput> invalidInputs, UserInput input) {
		AminoAcid refAA = AminoAcid.fromOneLetter(input.getOneLetterRefAA());
		AminoAcid altAA = AminoAcid.fromOneLetter(input.getOneLetterAltAA());
		Set<Integer> codonPositions = refAA.changedPositions(altAA);
		if (codonPositions == null || codonPositions.isEmpty())
			codonPositions = new HashSet<>(Arrays.asList(1, 2, 3));

		Map<String, List<GenomeToProteinMapping>> mappings = variantRepository.getMappings(input.getAccession(), input.getProteinPosition(), codonPositions)
				.stream().collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

		for (String key : mappings.keySet()) {
			GenomeToProteinMapping mapping = mappings.get(key).get(0);
			String refAllele = mapping.getBaseNucleotide();
			boolean isReverse = mapping.isReverseStrand();
			String codon = mapping.getCodon().toUpperCase();

			RNACodon refRNACodon = RNACodon.valueOf(codon);
			int codonPosition = mapping.getCodonPosition();

			// Determining the alt allele
			// *from user input
			// ^from db
			// (X)missing
			// refAA*       | altAA*
			// refAllele^   | altAllele(X)		A/T/C/G
			// refRNACodon^ | altRNACodon(X)	A/U/C/G
			// codonPosition^

			List<RNACodon> altRNACodons_ = refRNACodon.getSNVs().stream()
					.filter(c -> c.getAa().equals(altAA))
					.collect(Collectors.toList());

			if (altRNACodons_.isEmpty()) {
				input.addInvalidReason(String.format("%s (%s) to %s %s not possible via SNV", refAA.getThreeLetters(), refRNACodon.name(), altAA.getThreeLetters(), altAA.getRnaCodons() ));
				input.setChromosome(mapping.getChromosome());
				input.setStart(mapping.getGenomeLocation());
				input.setRef(refAllele);
				input.setAlt(Constants.NA);
				input.setId(Constants.NA);
				invalidInputs.add(input);
			}

			char charAtCodonPos = refRNACodon.name().charAt(codonPosition-1);
			List<RNACodon> altRNACodons = altRNACodons_.stream()
					.filter(c -> c.name().charAt(codonPosition-1) != charAtCodonPos)
					.collect(Collectors.toList());

			Set<String> altAlleles = new HashSet<>();
			for (RNACodon altRNACodon : altRNACodons) {
				altAlleles.add(snvDiff(refRNACodon, altRNACodon));
			}

			for (String altAllele : altAlleles) {
				altAllele = isReverse ? RNACodon.reverse(altAllele) : altAllele;
				altAllele = altAllele.replace('U', 'T');
				UserInput newInput = UserInput.copy(input);
				newInput.setChromosome(mapping.getChromosome());
				newInput.setStart(mapping.getGenomeLocation());
				newInput.setRef(refAllele);
				newInput.setAlt(altAllele);
				validInputs.add(newInput);
			}
		}
	}

	private String snvDiff(RNACodon c1, RNACodon c2) {
		for (int p=0; p<3; p++) {
			if (c1.name().charAt(p) != c2.name().charAt(p))
				return String.valueOf(c2.name().charAt(p));
		}
		return null;
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
