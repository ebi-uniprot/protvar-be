package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.grc.Crossmap;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.RNACodon;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MappingFetcher {

	private ProtVarDataRepo protVarDataRepo;
	private Mappings2GeneConverter mappingsConverter;

	/**
	 * @return - GenomeProteinMapping - Object containing genomic and protein data
	 *         for a given chromosome and genomicLocation
	 */
	public GenomeProteinMapping getMapping(String chromosome, Long genomicLocation, String id, String allele,
			String variantAllele, List<OPTIONS> options) {
		List<GenomeToProteinMapping> mappings = protVarDataRepo.getMappings(chromosome, genomicLocation);
		if (mappings == null || mappings.isEmpty())
			return GenomeProteinMapping.builder().chromosome(chromosome).geneCoordinateStart(genomicLocation)
					.geneCoordinateEnd(genomicLocation).id(id).userAllele(allele).variantAllele(variantAllele)
					.genes(Collections.emptyList()).build();

		List<CADDPrediction> predictions = protVarDataRepo.getCADDPredictions(List.of(genomicLocation));

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
	public MappingResponse getMappings(List<String> inputs, List<OPTIONS> options, Assembly assembly) {
		MappingResponse response = new MappingResponse();
		List<UserInput> userInputs = new ArrayList<>();
		List<UserInput> invalidInputs = new ArrayList<>();
		List<UserInput> validInputs = new ArrayList<>();

		List<String> rsIds = new ArrayList<>();
		List<UserInput> grch37Inputs = new ArrayList<>();

		response.setInvalidInputs(invalidInputs);
		inputs.stream().map(String::trim)
				.filter(i -> !i.isEmpty())
				.filter(i -> !i.startsWith("#")).forEach(input -> {
					UserInput pInput = UserInput.getInput(input);
					userInputs.add(pInput);
					if (pInput.getType() == UserInput.Type.RS)
						rsIds.add(pInput.getId());

					if (assembly != null && assembly == Assembly.GRCH37 &&
							(pInput.getType() == UserInput.Type.VCF || pInput.getType() == UserInput.Type.HGVS)) {
						grch37Inputs.add(pInput);
					}
				});

		Map<String, List<Dbsnp>> dbsnpMap = protVarDataRepo.getDbsnps(rsIds).stream().collect(Collectors.groupingBy(Dbsnp::getId));

		if (assembly != null && assembly == Assembly.GRCH37) {
			List<Long> grch37Positions = new ArrayList<>();
			for (UserInput grch37Input : grch37Inputs) {
				if (grch37Input.isValid()) {
					grch37Positions.add(grch37Input.getStart());
				}
			}
			Map<String, List<Crossmap>> groupedCrossmaps = protVarDataRepo.getCrossmaps(grch37Positions, assembly.version)
					.stream().collect(Collectors.groupingBy(Crossmap::getGroupByChrAnd37Pos));
			for (UserInput grch37Input : grch37Inputs) {
				if (grch37Input.isValid()) {
					String chr = grch37Input.getChromosome();
					Long pos = grch37Input.getStart();
					List<Crossmap> crossmaps = groupedCrossmaps.get(chr+"-"+pos);
					if (crossmaps == null || crossmaps.isEmpty()) {
						grch37Input.addInvalidReason(String.format("No equivalent GRCh38 coordinate found for GRCh37 coordinate (%s,%s)", chr, pos));
					} else if (crossmaps.size()==1) {
						grch37Input.setStart(crossmaps.get(0).getGrch38Pos());
					} else {
						grch37Input.addInvalidReason(String.format("Multiple mappings for GRCh37 coordinate (%s,%s)", chr, pos));
					}
				}
			}
		}

		userInputs.stream().forEach(pInput -> {
					if (pInput.isValid()) {
						if (pInput.getType() == UserInput.Type.PROTAC)
							addNewInputsUsingGenomicFromProteinInfo(validInputs, invalidInputs, pInput);
						else if (pInput.getType() == UserInput.Type.RS)
							addRsInputs(validInputs, invalidInputs, pInput, dbsnpMap);
						else
							validInputs.add(pInput);
					} else
						invalidInputs.add(pInput);
				});

		List<Long> positions = validInputs.stream().map(UserInput::getStart).distinct().collect(Collectors.toList());
		if (!positions.isEmpty()) {

			Map<String, List<CADDPrediction>> predictionMap = protVarDataRepo.getCADDPredictions(positions)
				.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

			List<GenomeToProteinMapping> g2pMappings = protVarDataRepo.getMappings(positions);
			List <String> proteinAccessions = new ArrayList<>();
			List <Integer> proteinPositions = new ArrayList<>();
			g2pMappings.forEach(m -> {
				proteinAccessions.add(m.getAccession());
				proteinPositions.add(m.getIsoformPosition());
			});
			Map<String, List<EVEScore>> eveScoreMap = protVarDataRepo.getEVEScores(proteinAccessions, proteinPositions)
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

		Map<String, List<GenomeToProteinMapping>> mappings = protVarDataRepo.getMappings(input.getAccession(), input.getProteinPosition(), codonPositions)
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

	private void addRsInputs(List<UserInput> validInputs, List<UserInput> invalidInputs, UserInput input, Map<String, List<Dbsnp>> dbsnpMap) {
		String id = input.getId();
		List<Dbsnp> rsInputVariants = dbsnpMap.get(id);
		if (rsInputVariants != null && !rsInputVariants.isEmpty()) {
			for (Dbsnp v: rsInputVariants) {
				for (String alt : v.getAlt().split(",")) {
					UserInput newInput = UserInput.rsInput(id);
					newInput.setChromosome(v.getChr());
					newInput.setStart(v.getPos());
					newInput.setRef(v.getRef());
					newInput.setAlt(alt);
					validInputs.add(newInput);
				}
			}
		} else {
			input.addInvalidReason(String.format("Variant ID %s not found", id));
			input.setChromosome(Constants.NA);
			input.setRef(Constants.NA);
			input.setAlt(Constants.NA);
			invalidInputs.add(input);
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
