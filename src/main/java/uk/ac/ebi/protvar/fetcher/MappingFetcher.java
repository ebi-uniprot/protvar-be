package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.cache.UniprotEntryCache;
import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.input.mapper.ID2Gen;
import uk.ac.ebi.protvar.input.mapper.Pro2Gen;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.model.data.CADDPrediction;
import uk.ac.ebi.protvar.model.data.EVEScore;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.data.Crossmap;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MappingFetcher {
	private static final Logger logger = LoggerFactory.getLogger(MappingFetcher.class);

	private ProtVarDataRepo protVarDataRepo;

	private ID2Gen id2Gen;
	private Pro2Gen pro2Gen;
	private Mappings2GeneConverter mappingsConverter;

	private ProteinsFetcher proteinsFetcher;

	private VariationFetcher variationFetcher;


	@Autowired
	UniprotEntryCache uniprotEntryCache;

	/**
	 * Takes a list of input strings and return corresponding list of userInput objects
	 * @param inputs
	 * @return
	 */
	public List<UserInput> parseUserInputStrIntoObject(List<String> inputs) {
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
		 *
		 *	II	group by input type
		 *
		 *	III build response map for each input
		 *		each input -> [] of possible output
		 *		genomic coords input can have multiple outputs per input, if overlapping genes, or genes in both directions
		 *		Protein & RS inputs can also have multiple outputs per input
		 */

		MappingResponse response = new MappingResponse();
		List<Message> messages = new ArrayList<>();
		response.setMessages(messages);

		// Step 1a - parse input strings into UserInput objects
		List<UserInput> userInputs = parseUserInputStrIntoObject(inputs);
		response.setInputs(userInputs);

		// Step 1b - generate input summary
		String inputSummary = inputSummary(userInputs);
		messages.add(new Message(Message.MessageType.INFO, inputSummary));


		// Step 2 - group user inputs into input type: List<UserInput> -> Map<Type, List<UserInput>>
		Map<Type, List<UserInput>> groupedInputs = userInputs.stream().filter(UserInput::isValid) // filter out any invalid inputs
				.collect(Collectors.groupingBy(UserInput::getType));

		// Type			Format									Required processing
		// GENOMIC		VCF, HGVS_GEN, GNOMAD, CUSTOM_GEN		if onlyGenomicInput && h37 build specified, convert coords
		// CODING 		HGVS_CODING								refseq NM_ mapping ??
		// PROTEIN		HGVS_PROT, CUSTOM_PROT					get genomic coords (0..*) from g2p_mapping tbl
		// ID			DBSNP, CLINVAR, COSMIC					get genomic coords (0..*) from dbsnp/clinvar/cosmic tbl

		// Step 3 - process each input type


		// GenomicInputProcessor
		// ProteinInputProcessor
		// IDInputProcessor
		// CodingInputProcessor





		// TODO
		// for genomic input,
		// if both ref and alt provided, check ref equals db ref
		// if only one base provided, check if base equals ref,
		//    if yes, use 3 alt bases i.e. add new genomic input to results
		//    if not, assume base provided is alt, use db ref as ref
		// if no base provided, use db ref + 3 alt bases

		// genomic inputs - VCF, HGVS, gnomAD
		if (groupedInputs.containsKey(Type.GENOMIC)) {
			List<UserInput> genomicInputs = groupedInputs.get(Type.GENOMIC);
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

		// process ID inputs
		if (groupedInputs.containsKey(Type.ID)) {
			List<UserInput> idInputs = groupedInputs.get(Type.ID);
			id2Gen.convert(idInputs);
		}

		// process Protein inputs
		if (groupedInputs.containsKey(Type.PROTEIN)) {
			List<UserInput> proteinInputs = groupedInputs.get(Type.PROTEIN);

			proteinInputs.stream().map(i -> (ProteinInput) i).forEach(protInput -> {
				if (!uniprotEntryCache.isValidEntry(protInput.getAcc())) {
					protInput.addError("Invalid accession " + protInput.getAcc());
				}
			});
			pro2Gen.convert(proteinInputs);
		}

		// get all chrPos combination

		List<Object[]> chrPosList = new ArrayList<>();
		userInputs.stream().forEach(userInput -> {
			chrPosList.addAll(userInput.chrPos());
		});

		if (!chrPosList.isEmpty()) {

			// retrieve CADD predictions
			Map<String, List<CADDPrediction>> predictionMap = protVarDataRepo.getCADDByChrPos(chrPosList)
					.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

			// retrieve main mappings
			List<GenomeToProteinMapping> g2pMappings = protVarDataRepo.getMappingsByChrPos(chrPosList);

			// get all protein accessions and positions from retrieved mappings
			Set<String> canonicalAccessions = new HashSet<>();
			Set<String> canonicalAccessionLocations = new HashSet<>();
			g2pMappings.stream().filter(GenomeToProteinMapping::isCanonical).forEach(m -> {
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
			Map<String, List<EVEScore>> eveScoreMap = protVarDataRepo.getEVEScores(canonicalAccessionLocations)
					.stream().collect(Collectors.groupingBy(EVEScore::getGroupBy));

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			userInputs.stream().filter(UserInput::isValid).forEach(input -> {

				List<GenomicInput> gInputs = new ArrayList<>();
				gInputs.addAll(input.genInputs());

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
			Integer pos = input.getPos();
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
		int[] counts = {0,0,0,0,0}; //genomic, coding, protein, ID, !valid

		userInputs.stream().forEach(input -> {
			if (input.getType() == Type.GENOMIC) counts[0]++;
			else if (input.getType() == Type.CODING) counts[1]++;
			else if (input.getType() == Type.PROTEIN) counts[2]++;
			else if (input.getType() == Type.ID) counts[3]++;

			if (!input.isValid()) counts[4]++;
		});
		List<String> inputTypes = new ArrayList<>();
		if (counts[0] > 0) inputTypes.add(String.format("%d genomic", counts[0]));
		if (counts[1] > 0) inputTypes.add(String.format("%d coding", counts[1]));
		if (counts[2] > 0) inputTypes.add(String.format("%d protein", counts[2]));
		if (counts[3] > 0) inputTypes.add(String.format("%d ID", counts[3]));

		if (inputTypes.size() > 1) inputSummary += "(" + String.join(", ", inputTypes) + "). ";

		if (counts[4] > 0) inputSummary += String.format("%d input%s %s not valid. ", counts[4], FetcherUtils.pluralise(counts[4]), FetcherUtils.isOrAre(counts[4]));
		return inputSummary;
	}
}
