package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.model.data.CADDPrediction;
import uk.ac.ebi.protvar.model.data.Dbsnp;
import uk.ac.ebi.protvar.model.data.EVEScore;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.data.Crossmap;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.utils.RNACodon;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MappingFetcher {
	private static final Logger logger = LoggerFactory.getLogger(MappingFetcher.class);

	private ProtVarDataRepo protVarDataRepo;
	private Mappings2GeneConverter mappingsConverter;

	private ProteinsFetcher proteinsFetcher;

	private VariationFetcher variationFetcher;

	/**
	 * Takes a list of input strings and return corresponding list of userInput objects
	 * @param inputs
	 * @return
	 */
	public List<UserInput> processUserInputs(List<String> inputs) {
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
	 * @return MappingResponse
	 */
	public MappingResponse getMappings(List<String> inputs, List<OPTIONS> options, String assemblyVersion) {
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

		MappingResponse response = new MappingResponse();
		List<Message> messages = new ArrayList<>();
		response.setMessages(messages);

		// Step I
		List<UserInput> userInputs = processUserInputs(inputs);
		response.setInputs(userInputs);

		String inputSummary = inputSummary(userInputs);
		messages.add(new Message(Message.MessageType.INFO, inputSummary));


		// Step II - List<UserInput> to Map<InputType, List<UserInput>>
		Map<InputType, List<UserInput>> groupedInputs = userInputs.stream().filter(UserInput::isValid).collect(Collectors.groupingBy(UserInput::getType));

		// genomic inputs - VCF, HGVS, gnomAD
		if (groupedInputs.containsKey(InputType.GEN)) {
			List<UserInput> genomicInputs = groupedInputs.get(InputType.GEN);
			boolean allGenomic = groupedInputs.size() == 1;

			/**   assembly
			 *   |    |    \
			 *   v    v     \
			 *  null auto    v
			 *   |    |     37or38
			 *   | detect   |    |  \
			 *   | |   \    |    |  /undetermined(assumes)
			 *    \ \   v   v    v  v
			 *     \ \  is37 ..> is38
			 *      \_\___________^
			 *
			 *        ..> converts to
			 */

			// null | auto | 37or38
			Assembly assembly = null;

			if (assemblyVersion == null) {
				messages.add(new Message(Message.MessageType.WARN, "Unspecified assembly version; defaulting to GRCh38. "));
				assembly = Assembly.GRCH38;
			} else {
				if (assemblyVersion.equalsIgnoreCase("AUTO")) {
					if (allGenomic) {
						assembly = determineInputBuild(genomicInputs, messages); // -> 37 or 38
					}
					else {
						messages.add(new Message(Message.MessageType.WARN, "Assembly auto-detect works for all-genomic inputs only; defaulting to GRCh38. "));
						assembly = Assembly.GRCH38;
					}
				} else {
					assembly = Assembly.of(assemblyVersion);
					if (assembly == null) {
						messages.add(new Message(Message.MessageType.WARN, "Unable to determine assembly version; defaulting to GRCh38. "));
						assembly = Assembly.GRCH38;
					}
				}
			}

			if (assembly == Assembly.GRCH37) {
				messages.add(new Message(Message.MessageType.INFO, "Converting GRCh37 to GRCh38. "));
				processGenomicInputs(genomicInputs);
			}

		}

		// RS inputs
		if (groupedInputs.containsKey(InputType.RS)) {
			List<UserInput> rsInputs = groupedInputs.get(InputType.RS);
			processRSInputs(rsInputs);
		}

		// Protein inputs
		if (groupedInputs.containsKey(InputType.PRO)) {
			List<UserInput> proteinInputs = groupedInputs.get(InputType.PRO);
			processProteinInputs(proteinInputs);
		}

		// get all genomic positions
		Set<Long> gPositions = new HashSet<>();
		userInputs.stream().forEach(i -> {
			if (i instanceof GenomicInput) {
				gPositions.add(((GenomicInput) i).getPos());
			} else if (i instanceof RSInput) {
				for (GenomicInput gInput :((RSInput) i).getDerivedGenomicInputs()) {
					gPositions.add(gInput.getPos());
				}
			} else if (i instanceof ProteinInput) {
				for (GenomicInput gInput :((ProteinInput) i).getDerivedGenomicInputs()) {
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
			Set<Object[]> canonicalAccessionPositions = new HashSet<>();
			Set<String> canonicalAccessions = new HashSet<>();
			Set<String> canonicalAccessionLocations = new HashSet<>();
			g2pMappings.stream().filter(GenomeToProteinMapping::isCanonical).forEach(m -> {
				canonicalAccessionPositions.add(new Object[]{m.getAccession(), m.getIsoformPosition()});
				canonicalAccessions.add(m.getAccession());
				canonicalAccessionLocations.add(m.getAccession() + ":" + m.getIsoformPosition());
			});

			options.parallelStream().forEach(o -> {
				if (o.equals(OPTIONS.FUNCTION))
					proteinsFetcher.prefetch(canonicalAccessions);
				if (o.equals(OPTIONS.POPULATION))
					variationFetcher.prefetch(canonicalAccessionLocations);
			});

			// retrieve EVE scores
			Map<String, List<EVEScore>> eveScoreMap = protVarDataRepo.getEVEScores(canonicalAccessionPositions)
					.stream().collect(Collectors.groupingBy(EVEScore::getGroupBy));

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			userInputs.stream().filter(UserInput::isValid).forEach(input -> {

				List<GenomicInput> gInputs = new ArrayList<>();

				if (input instanceof GenomicInput) {
					gInputs.add((GenomicInput) input);
				} else if (input instanceof RSInput) {
					gInputs.addAll(((RSInput) input).getDerivedGenomicInputs());
				} else if (input instanceof ProteinInput) {
					gInputs.addAll(((ProteinInput) input).getDerivedGenomicInputs());
				}

				gInputs.forEach(gInput -> {
					try {
						List<GenomeToProteinMapping> mappingList = map.get(gInput.getGroupBy());
						List<CADDPrediction> caddScores = predictionMap.get(gInput.getGroupBy());

						Double caddScore = null;
						if (caddScores != null && !caddScores.isEmpty())
							caddScore = getCaddScore(caddScores, gInput.getAlt());

						List<Gene> ensgMappingList = Collections.emptyList();
						if (mappingList != null)
							ensgMappingList = mappingsConverter.createGenes(mappingList, gInput.getRef(), gInput.getAlt(), caddScore, eveScoreMap, options);

						GenomeProteinMapping mapping = GenomeProteinMapping.builder().genes(ensgMappingList).build();
						gInput.getMappings().add(mapping);
					} catch (Exception ex) {
						gInput.getErrors().add("An exception occurred while processing this input");
						logger.error(ex.getMessage());
					}
				});
			});

		}
		return response;
	}

	// select distinct chr,grch38_pos,grch38_base from crossmap where (chr,grch38_pos,grch38_base) IN (('X',149498202,'C'),
	//('10',43118436,'A'),
	//('2',233760498,'G'))
	private Assembly determineInputBuild(List<UserInput> genomicInputs, List<Message> messages) {

		List<Object[]> chrPosRefList = new ArrayList<>();
		genomicInputs.stream().map(i -> (GenomicInput) i).forEach(input -> {
			chrPosRefList.add(new Object[]{input.getChr(), input.getPos(), input.getRef()});
		});

		double percentage38 = protVarDataRepo.getPercentageMatch(chrPosRefList, "38");
		if (percentage38 > 50) { // assumes 38
			messages.add(new Message(Message.MessageType.INFO, String.format("Determined assembly version is GRCh38 (%.2f%% match of user inputs). ", percentage38)));
			return Assembly.GRCH38;
		} else {
			double percentage37 = protVarDataRepo.getPercentageMatch(chrPosRefList, "37");
			if (percentage37 > 50) { // assumes 37
				messages.add(new Message(Message.MessageType.INFO, String.format("Determined assembly version is GRCh38 (%.2f%% match of user inputs). ", percentage37)));
				return Assembly.GRCH37;
			} else {
				String msg = String.format("Undetermined assembly version (%.2f%% GRCh38 match, %.2f%% GRCh37 match). ", percentage38, percentage37);
				if (percentage37 > percentage38) {
					messages.add(new Message(Message.MessageType.INFO, msg + " Assuming GRCh37. "));
					return Assembly.GRCH37;
				}
				else {
					messages.add(new Message(Message.MessageType.INFO, msg + " Assuming GRCh38. "));
					return Assembly.GRCH38;
				}
			}
		}
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
				input.addError("No GRCh38 equivalent found for input coordinate. ");
			} else if (crossmaps.size()==1) {
				input.setPos(crossmaps.get(0).getGrch38Pos());
				input.setConverted(true);
			} else {
				input.addError("Multiple GRCh38 equivalents found for input coordinate. ");
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
						input.getDerivedGenomicInputs().add(newInput);
					}
				});
			}
			else {
				input.addError("Could not map RS ID to genomic coordinate(s).");
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
	- does GGA encodes Gly(G)?

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

		// 3. we expect each protein input to have 3 genomic coordinates (in normal cases),
		//	which we will try to pin down to one, if possible, based on the user inputs
		proteinInputs.stream().map(i -> (ProteinInput) i).forEach(input -> {


			AminoAcid refAA = AminoAcid.fromOneLetter(input.getRef());
			AminoAcid altAA = AminoAcid.fromOneLetter(input.getAlt());

			Set<AminoAcid> possibleAltAAs = new HashSet();
			for (RNACodon refCodon : refAA.getRnaCodons()) {
				possibleAltAAs.addAll(refCodon.getAltAAs());
			}

			if (!possibleAltAAs.contains(altAA)) {
				input.addInfo(String.format("%s to %s is a non-SNV. ", refAA.name(), altAA.name()));
			}

			// ref -> alt change is only possible by a SNV change at these
			// positions e.g. {1}, {2}, {1,2}, etc
			final Set<Integer> codonPositions = refAA.changedPositions(altAA);
			//if (codonPositions == null || codonPositions.isEmpty()) // means ref->alt AA not possible via SNV?
			//	codonPositions = new HashSet<>(Arrays.asList(1, 2, 3));

			String key = input.getAcc() +"-"+ input.getPos();
			List<GenomeToProteinMapping> gCoordsForProtein = gCoords.get(key);
			Set<String> seen = new HashSet<>();

			if (gCoordsForProtein != null && !gCoordsForProtein.isEmpty()) {
				gCoordsForProtein.forEach(gCoord -> {
					String gCoordChr = gCoord.getChromosome();
					Long gCoordPos = gCoord.getGenomeLocation();
					String gCoordRefAllele = gCoord.getBaseNucleotide();
					//String gCoordAcc = gCoord.getAccession();
					//Integer gCoordProteinPos = gCoord.getIsoformPosition();
					String gCoordRefAA = gCoord.getAa();
					String gCoordCodon = gCoord.getCodon(); //should code for/translate into refAA
					Integer gCoordCodonPos = gCoord.getCodonPosition();
					Boolean gCoordIsReverse = gCoord.isReverseStrand();

					String curr = gCoordChr + "-" + gCoordPos + "-" + gCoordRefAllele;
					if (seen.contains(curr)) return;
					seen.add(curr);

					if (codonPositions != null && !codonPositions.isEmpty()
						&& !codonPositions.contains(gCoordCodonPos)) return;

					RNACodon refRNACodon = RNACodon.valueOf(gCoordCodon.toUpperCase());

					Set<RNACodon> altRNACodons_ = refRNACodon.getSNVs().stream()
							.filter(c -> c.getAa().equals(altAA))
							.collect(Collectors.toSet());

					if (altRNACodons_.isEmpty()) {
						return;
					}

					char charAtCodonPos = refRNACodon.name().charAt(gCoordCodonPos-1); // = refAllele?
					List<RNACodon> altRNACodons = altRNACodons_.stream()
							.filter(c -> c.name().charAt(gCoordCodonPos-1) != charAtCodonPos)
							.collect(Collectors.toList());

					if (altRNACodons.isEmpty()) {
						return;
					}

					Set<String> altAlleles = new HashSet<>();
					for (RNACodon altRNACodon : altRNACodons) {
						altAlleles.add(snvDiff(refRNACodon, altRNACodon));
					}

					for (String altAllele : altAlleles) {
						altAllele = gCoordIsReverse ? RNACodon.reverse(altAllele) : altAllele;
						altAllele = altAllele.replace('U', 'T');
						GenomicInput gInput = new GenomicInput();
						gInput.setChr(gCoordChr);
						gInput.setPos(gCoordPos);
						gInput.setRef(gCoordRefAllele);
						gInput.setAlt(altAllele);
						gInput.setInputStr(input.getInputStr());
						input.getDerivedGenomicInputs().add(gInput);
						if (!refAA.getOneLetter().equalsIgnoreCase(gCoordRefAA))
							input.getMessages().add(new Message(Message.MessageType.WARN, "User reference and mapping record AA mismatch ("+refAA.name()+" vs. "+refRNACodon.name()+")"));
					}
				});
			}

			if (input.getDerivedGenomicInputs().isEmpty()) {
				input.addError("Could not map Protein input to genomic coordinate(s).");
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

	public String inputSummary(List<UserInput> userInputs) {
		String inputSummary = String.format("Processed %d input%s ", userInputs.size(), FetcherUtils.pluralise(userInputs.size()));
		int[] counts = {0,0,0,0}; //genomic, protein, rs, !valid

		userInputs.stream().forEach(input -> {
			if (input.getType() == InputType.GEN) counts[0]++;
			else if (input.getType() == InputType.PRO) counts[1]++;
			else if (input.getType() == InputType.RS) counts[2]++;

			if (!input.isValid()) counts[3]++;
		});
		List<String> inputTypes = new ArrayList<>();
		if (counts[0] > 0) inputTypes.add(String.format("%d genomic", counts[0]));
		if (counts[1] > 0) inputTypes.add(String.format("%d protein", counts[1]));
		if (counts[2] > 0) inputTypes.add(String.format("%d RS ID", counts[2]));

		if (inputTypes.size() > 1) inputSummary += "(" + String.join(", ", inputTypes) + "). ";

		if (counts[3] > 0) inputSummary += String.format("%d input%s %s not valid. ", counts[3], FetcherUtils.pluralise(counts[3]), FetcherUtils.isOrAre(counts[3]));
		return inputSummary;
	}
}
