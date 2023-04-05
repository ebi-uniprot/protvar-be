package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.input.*;
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
	 * Takes a list of input strings and return corresponding list of userInput objects
	 * @param inputs
	 * @return
	 */
	public List<UserInput> getUserInputs(List<String> inputs) {
		List<UserInput> userInputs = new ArrayList<>();
		inputs.stream().map(String::trim)
				.filter(i -> !i.isEmpty())
				.filter(i -> !i.startsWith("#")).forEach(input -> {
					UserInput pInput = UserInput.getInput(input);
					userInputs.add(pInput);
				});
		return userInputs;
	}

	/**
	 * 
	 * @param inputs is list of input string in various formats - VCF, HGVS, Protein, dbSNP ID, gnomAD
	 * @return - List of GenomeProteinMapping - Object containing genomic and
	 *         protein data for a given user input
	 */
	public List<MappingResponse> getMappings(List<String> inputs, List<OPTIONS> options, Assembly assembly) {
		/**
		 * Steps
		 *	I	parse input string - each input will be either one of the following
		 *			1. RS			- valid (matches regex == valid, but may nt map to a gcoord.)
		 *			2. gnomAD		- valid (matches regex == valid)
		 *			3. HGVS			- valid or invalid (e.g. starts with NC_ but couldn't parse remaining params)
		 *			4. Protein		- valid or invalid (e.g. starts with a protein accession, but remaining params invalid)
		 *			5. VCF			- valid or invalid (e.g. missing param or invalid param type)
		 *
		 *	II	group by input type
		 *		1. VCF, HGVS and gnomAD inputs, no extra step unless h37 specified
		 *			if h37, retrieve converted coordinates
		 *			mark input as "converted"
		 *		2. RS inputs, get genomic coordinates, h38 assumed, no conversion  -> dbsnp tbl lookup to obtain (0..*) genomic coords
		 *		3. Protein inputs, get genomic coordinates, h38 assumed, no conversion -> mapping tbl lookup to obtain (0..*) genomic coords
		 *
		 *	III build response map for each input
		 *		each input -> [] of possible output
		 *		genomic coords input can have multiple outputs per input, if overlapping genes, or genes in both directions
		 *		Protein & RS inputs can also have multiple outputs per input
		 */

		List<MappingResponse> results = new ArrayList<>();

		// Step I
		List<UserInput> userInputs = getUserInputs(inputs);

		// Step II - List<UserInput> to Map<InputType, List<UserInput>>
		Map<InputType, List<UserInput>> groupedInputs = userInputs.stream().filter(UserInput::isValid).collect(Collectors.groupingBy(UserInput::getType));

		// genomic inputs - VCF, HGVS, gnomAD
		if (groupedInputs.containsKey(InputType.GEN)) {
			List<UserInput> genomicInputs = groupedInputs.get(InputType.GEN);

			if (assembly != null && assembly == Assembly.GRCH37)
				processGenomicInputs(genomicInputs);
		}

		// auto-detect
		// if all inputs is GEN and auto-detect selected
		// select distinct chr,grch38_pos,grch38_base from crossmap where (chr,grch38_pos,grch38_base) IN (('X',149498202,'C'),
		//('10',43118436,'A'),
		//('2',233760498,'G'))
		// >50% matches - assumes 38 (msg X% of the genomic inputs matches GRCh38 build)
		// else
		// select distinct chr,grch37_pos,grch37_base from crossmap where (chr,grch37_pos,grch37_base) IN (('X',149498202,'C'),
		//('10',43118436,'A'),
		//('2',233760498,'G'))
		// 100 inputs (34 genomic, 23 protein, 34 rs, x invalid)
		//

		// RS inputs
		if (groupedInputs.containsKey(InputType.RS))
			processRSInputs(groupedInputs.get(InputType.RS));

		// Protein inputs
		if (groupedInputs.containsKey(InputType.PRO))
			processProteinInputs(groupedInputs.get(InputType.PRO));

		// get all genomic positions
		Set<Long> gPositions = new HashSet<>();
		userInputs.stream().forEach(i -> {
			if (i instanceof GenomicInput) {
				gPositions.add(((GenomicInput) i).getPos());
			} else if (i instanceof RSInput) {
				for (GenomicInput gInput :((RSInput) i).getGenomicInputList()) {
					gPositions.add(gInput.getPos());
				}
			} else if (i instanceof ProteinInput) {
				for (GenomicInput gInput :((ProteinInput) i).getGenomicInputList()) {
					gPositions.add(gInput.getPos());
				}
			}
		});

		if (!gPositions.isEmpty()) {

			// retrieve CADD predictions
			Map<String, List<CADDPrediction>> predictionMap = protVarDataRepo.getCADDPredictions(gPositions)
					.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

			// retrieve main mappings

			List<GenomeToProteinMapping> g2pMappings = protVarDataRepo.getMappings(gPositions);

			// get all protein accessions and positions from retrieved mappings
			List<Object[]> protAccPositions = new ArrayList<>();
			g2pMappings.forEach(m -> {
				protAccPositions.add(new Object[]{m.getAccession(), m.getIsoformPosition()});
			});

			// retrieve EVE scores
			Map<String, List<EVEScore>> eveScoreMap = protVarDataRepo.getEVEScores(protAccPositions)
					.stream().collect(Collectors.groupingBy(EVEScore::getGroupBy));

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			userInputs.stream().filter(UserInput::isValid).forEach(input -> {

				List<GenomicInput> gInputs = new ArrayList<>();

				if (input instanceof GenomicInput) {
					gInputs.add((GenomicInput) input);
				} else if (input instanceof RSInput) {
					gInputs.addAll(((RSInput) input).getGenomicInputList());
				} else if (input instanceof ProteinInput) {
					gInputs.addAll(((ProteinInput) input).getGenomicInputList());
				}

				gInputs.forEach(gInput -> {
					List<GenomeToProteinMapping> mappingList = map.get(gInput.getGroupBy());
					List<CADDPrediction> caddScores = predictionMap.get(gInput.getGroupBy());

					Double caddScore = null;
					if (caddScores != null && !caddScores.isEmpty())
						caddScore = getCaddScore(caddScores, gInput.getAlt());

					List<Gene> ensgMappingList = Collections.emptyList();
					if (mappingList != null)
						ensgMappingList = mappingsConverter.createGenes(mappingList, gInput.getRef(), gInput.getAlt(), caddScore, eveScoreMap, options);

					GenomeProteinMapping mapping = GenomeProteinMapping.builder().chromosome(gInput.getChr())
							.geneCoordinateStart(gInput.getPos()).id(gInput.getId()).geneCoordinateEnd(gInput.getPos())
							.userAllele(gInput.getRef()).variantAllele(gInput.getAlt()).genes(ensgMappingList)
							.input(gInput.getInputStr()).build();
					gInput.getMappings().add(mapping);
				});
			});

		}


		userInputs.stream().forEach(i -> {
			results.add(new MappingResponse(i.getInputStr(), i.getMappings()));
		});



		//List<GenomeProteinMapping> listOfMappings = userInputs.stream().map(UserInput::getMappings).flatMap(List::stream).collect(Collectors.toList());
		//response.setMappings(listOfMappings);

		return results;
	}

	/**
	 * Process genomic inputs
	 * - this is required if an assembly conversion is needed
	 * - note that if multiple equivalents are found, these are not added as new inputs but is considered invalid.
	 * - genomic inputs may have multiple outputs for e.g. overlapping genes in same or both directions.
	 * - the latter is tackled in the main mapping logic.
	 * @param genomicInputs
	 */
	private void processGenomicInputs(List<UserInput> genomicInputs) {

		List<Object[]> chrPos37 = new ArrayList<>();
		genomicInputs.stream().map(i -> (GenomicInput) i).forEach(input -> {
			chrPos37.add(new Object[]{input.getChr(), input.getPos()});
		});

		Map<String, List<Crossmap>> groupedCrossmaps = protVarDataRepo.getCrossmapsByChrPos37(chrPos37)
				.stream().collect(Collectors.groupingBy(Crossmap::getGroupByChrAnd37Pos));

		genomicInputs.stream().map(i -> (GenomicInput) i).forEach(input -> {

			String chr = input.getChr();
			Long pos = input.getPos();
			List<Crossmap> crossmaps = groupedCrossmaps.get(chr+"-"+pos);
			if (crossmaps == null || crossmaps.isEmpty()) {
				input.addInvalidReason(String.format("No equivalent GRCh38 coordinate found for GRCh37 coordinate (%s,%s)", chr, pos));
			} else if (crossmaps.size()==1) {
				input.setPos(crossmaps.get(0).getGrch38Pos());
				input.setConverted(true);
			} else {
				input.addInvalidReason(String.format("Multiple GRCh38 equivalents found for GRCh37 coordinate (%s,%s)", chr, pos));
			}
		});
	}

	/**
	 * Process RS inputs
	 * - it is possible that an RS ID gives multiple variants - in which case they are added to the genomicInputList
	 * @param rsInputs
	 */
	private void processRSInputs(List<UserInput> rsInputs) {
		List<String> rsInputIds = rsInputs.stream().map(i -> ((RSInput) i).getId()).collect(Collectors.toList());
		Map<String, List<Dbsnp>> dbsnpMap = protVarDataRepo.getDbsnps(rsInputIds).stream().collect(Collectors.groupingBy(Dbsnp::getId));

		rsInputs.stream().map(i -> (RSInput) i).forEach(input -> {
			String id = input.getId();
			List<Dbsnp> dbsnps = dbsnpMap.get(id);
			if (dbsnps != null && !dbsnps.isEmpty()) {
				dbsnps.forEach(dbsnp -> {
					String[] alts = dbsnp.getAlt().split(",");
					for (String alt : alts) {
						GenomicInput newInput = new GenomicInput();
						newInput.setChr(dbsnp.getChr());
						newInput.setPos(dbsnp.getPos());
						newInput.setRef(dbsnp.getRef());
						newInput.setAlt(alt);
						newInput.setId(id);
						newInput.setInputStr(input.getInputStr());
						input.getGenomicInputList().add(newInput);
					}
				});
			}
			else {
				input.addInvalidReason(String.format("Variant ID %s not found", id));
			}
		});
	}

	/*
	e.g. Protein input
	P22309 71 Gly Arg

	Check 1
	- can we go from Gly to Arg via SNP?

	select accession, protein_position, chromosome, genomic_position, allele, codon, reverse_strand, codon_position
	from genomic_protein_mapping
	where (accession, protein_position) IN (('P22309', 71))

	"accession"	"protein_position"	"chromosome"	"genomic_position"	"allele"	"codon"	"reverse_strand"	"codon_position"
	"P22309"	"71"	"2"	"233760498"	"G"	"Gga"	"false"	"1"
	"P22309"	"71"	"2"	"233760499"	"G"	"gGa"	"false"	"2"
	"P22309"	"71"	"2"	"233760500"	"A"	"ggA"	"false"	"3"

	Check 2
	- does GGA encodes Gly?

	Determining the single gCoord from the 3 gCoords representing the aa (Gly/GGA)
	i.e. determining the codon position (1,2, or 3)

	For each mapping
	"P22309"	"71"	"2"	"233760498"	"G"	"Gga"	"false"	"1"
	codon position = 1
	-> get possible SNVs (where G__ is fixed i.e. at position 1)
	-> check if alt AA is one of them, if not, skip mapping
	-> otherwise
		// determining alt allele from
		// - user input
		// refAA & altAA (e.g. Gly Arg)
		// - db mapping
		// ref allele (e.g. G)   -- NOTE: RNA allele (AUGC)
		// codon (e.g. Gga)      -- NOTE: DNA codon (ATGC)
		// codon position (e.g 1)

		-> possible SNVs at position 1 that encodes alt AA (Arg)
		-> for each possible SNV, diff with ref AA to find alt allele
		-> add each to genomic input list

	 */

	private void processProteinInputs(List<UserInput> proteinInputs) {
		// 1. get all the accessions and positions
		List<Object[]> accPPosition = new ArrayList<>();
		proteinInputs.stream().map(i -> (ProteinInput) i).forEach(input -> {
			accPPosition.add(new Object[]{input.getAcc(), input.getPos()});
		});

		// 2. get all the relevant db records by accessions and positions
		Map<String, List<GenomeToProteinMapping>> gCoords = protVarDataRepo.getGenomicCoordsByProteinAccAndPos(accPPosition)
						.stream().collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupByProteinAccAndPos));

		// 3. now for each protein input,
		// a. first check if refAA/altAA possible via SNP
		// b.
		proteinInputs.stream().map(i -> (ProteinInput) i).forEach(input -> {
			AminoAcid refAA = AminoAcid.fromOneLetter(input.getRef());
			AminoAcid altAA = AminoAcid.fromOneLetter(input.getAlt());

			Set<AminoAcid> possibleAltAAs = new HashSet();
			for (RNACodon refCodon : refAA.getRnaCodons()) {
				possibleAltAAs.addAll(refCodon.getAltAAs());
			}

			if (!possibleAltAAs.contains(altAA)) {
				// it is not possible to go from refAA to altAA via SNP.
				input.addInvalidReason(String.format("It is not possible to go from %s to %s via SNP", refAA.name(), altAA.name()));
				return;
			}

			// ref -> alt change is only possible by a SNV change at these
			// positions (e.g. {1}, {2}, {1,2}, etc
			final Set<Integer> codonPositions = refAA.changedPositions(altAA);
			//if (codonPositions == null || codonPositions.isEmpty())
			//	codonPositions = new HashSet<>(Arrays.asList(1, 2, 3));

			String key = input.getAcc() +"-"+ input.getPos();
			List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);
			Set<String> seen = new HashSet<>();
			if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
				gCoordsForProtein.forEach(gCoord -> {

					String curr = gCoord.getChromosome() + "-" + gCoord.getGenomeLocation() + "-" + gCoord.getBaseNucleotide();
					if (seen.contains(curr)) return;
					seen.add(curr);

					if (codonPositions != null && !codonPositions.isEmpty()
						&& !codonPositions.contains(gCoord.getCodonPosition())) return;

					String refAllele = gCoord.getBaseNucleotide();
					boolean isReverse = gCoord.isReverseStrand();
					String codon = gCoord.getCodon().toUpperCase();

					RNACodon refRNACodon = RNACodon.valueOf(codon);
					int codonPosition = gCoord.getCodonPosition();

					Set<RNACodon> altRNACodons_ = refRNACodon.getSNVs().stream()
							.filter(c -> c.getAa().equals(altAA))
							.collect(Collectors.toSet());

					if (altRNACodons_.isEmpty()) {
						return;
					}

					char charAtCodonPos = refRNACodon.name().charAt(codonPosition-1); // = refAllele?
					List<RNACodon> altRNACodons = altRNACodons_.stream()
							.filter(c -> c.name().charAt(codonPosition-1) != charAtCodonPos)
							.collect(Collectors.toList());

					if (altRNACodons.isEmpty()) {
						return;
					}

					Set<String> altAlleles = new HashSet<>();
					for (RNACodon altRNACodon : altRNACodons) {
						altAlleles.add(snvDiff(refRNACodon, altRNACodon));
					}

					for (String altAllele : altAlleles) {
						altAllele = isReverse ? RNACodon.reverse(altAllele) : altAllele;
						altAllele = altAllele.replace('U', 'T');
						GenomicInput gInput = new GenomicInput();
						gInput.setChr(gCoord.getChromosome());
						gInput.setPos(gCoord.getGenomeLocation());
						gInput.setRef(refAllele);
						gInput.setAlt(altAllele);
						gInput.setInputStr(input.getInputStr());
						input.getGenomicInputList().add(gInput);
					}

					if (input.getGenomicInputList().isEmpty()) {
						input.addInvalidReason(String.format("Could not map protein (%s, %d) to genomic coordinates", input.getAcc(), input.getPos()));
					}

				});
			}
			else {
				input.addInvalidReason(String.format("Could not map protein (%s, %d) to genomic coordinates", input.getAcc(), input.getPos()));
			}

		});

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
