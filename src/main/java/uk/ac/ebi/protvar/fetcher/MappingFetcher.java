package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.mapper.Coding2Pro;
import uk.ac.ebi.protvar.input.mapper.ID2Gen;
import uk.ac.ebi.protvar.input.mapper.Pro2Gen;
import uk.ac.ebi.protvar.input.processor.BuildConverter;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.Coord;
import uk.ac.ebi.protvar.model.data.CADDPrediction;
import uk.ac.ebi.protvar.model.data.EVEScore;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.FetcherUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MappingFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(MappingFetcher.class);

	private ProtVarDataRepo protVarDataRepo;

	private ID2Gen id2Gen;
	private Pro2Gen pro2Gen;
	private Mappings2GeneConverter mappingsConverter;

	private ProteinsFetcher proteinsFetcher;

	private VariationFetcher variationFetcher;

	private BuildConverter buildConverter;

	private Coding2Pro coding2Pro;



	/**
	 * Takes a list of input strings and return corresponding list of userInput objects
	 * @param inputs
	 * @return
	 */
	public List<UserInput> parseInputStrIntoObject(List<String> inputs) {
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
		MappingResponse response = new MappingResponse();
		List<Message> messages = new ArrayList<>();
		response.setMessages(messages);

		// Step 1a - parse input strings into UserInput objects - of specific type and format
		List<UserInput> userInputs = parseInputStrIntoObject(inputs);
		response.setInputs(userInputs);

		// Step 1b - generate input summary
		String inputSummary = inputSummary(userInputs);
		messages.add(new Message(Message.MessageType.INFO, inputSummary));


		// Step 2 - group user inputs into input type: List<UserInput> -> Map<Type, List<UserInput>>
		Map<Type, List<UserInput>> groupedInputs = userInputs.stream().filter(UserInput::isValid) // filter out any invalid inputs
				.collect(Collectors.groupingBy(UserInput::getType));

		// note:
		// - all non-genomic input is ultimately mapped to one or more genomic inputs
		// - each genomic input will return a list of mappings (output)

		// Type			Format									Required processing
		// GENOMIC		VCF, HGVS_GEN, GNOMAD, CUSTOM_GEN		if onlyGenomicInput && h37 build specified, convert coords
		// CODING 		HGVS_CODING								refseq NM_ mapping ??
		// PROTEIN		HGVS_PROT, CUSTOM_PROT					get genomic coords (0..*) from g2p_mapping tbl
		// ID			DBSNP, CLINVAR, COSMIC					get genomic coords (0..*) from dbsnp/clinvar/cosmic tbl

		// Step 3 - process each input type

		// TODO
		// for genomic input,
		// if both ref and alt provided, check ref equals db ref
		// if only one base provided, check if base equals ref,
		//    if yes, use 3 alt bases i.e. add new genomic input to results
		//    if not, assume base provided is alt, use db ref as ref
		// if no base provided, use db ref + 3 alt bases

		// genomic inputs - VCF, HGVS_GEN, GNOMAD, CUSTOM_GEN
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
						assembly = buildConverter.determineBuild(genomicInputs, messages); // -> 37 or 38
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
				buildConverter.convert(genomicInputs);
			}
		}

		// process ID inputs
		if (groupedInputs.containsKey(Type.ID)) {
			List<UserInput> idInputs = groupedInputs.get(Type.ID);
			id2Gen.convert(idInputs);
		}

		if (groupedInputs.containsKey(Type.CODING)) {
			List<UserInput> codingInputs = groupedInputs.get(Type.CODING);
			coding2Pro.convert(codingInputs);
		}

		// process Protein inputs
		if (groupedInputs.containsKey(Type.PROTEIN) || groupedInputs.containsKey(Type.CODING)) {
			List<UserInput> proteinInputs = new ArrayList<>();
			if (groupedInputs.get(Type.PROTEIN) != null)
				proteinInputs.addAll(groupedInputs.get(Type.PROTEIN));
			if (groupedInputs.get(Type.CODING) != null)
				proteinInputs.addAll(groupedInputs.get(Type.CODING));
			pro2Gen.convert(proteinInputs);
		}

		// get all chrPos combination

		Set<Object[]> chrPosSet = new HashSet<>();
		userInputs.stream().forEach(userInput -> {
			chrPosSet.addAll(userInput.chrPos());
		});

		if (!chrPosSet.isEmpty()) {

			// retrieve CADD predictions
			Map<String, List<CADDPrediction>> predictionMap = protVarDataRepo.getCADDByChrPos(chrPosSet)
					.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

			// retrieve main mappings
			List<GenomeToProteinMapping> g2pMappings = protVarDataRepo.getMappingsByChrPos(chrPosSet);

			// get all protein accessions and positions from retrieved mappings
			Set<String> canonicalAccessions = new HashSet<>();
			Set<Coord.Prot> protCoords = new HashSet<>();
			g2pMappings.stream().filter(GenomeToProteinMapping::isCanonical).forEach(m -> {
				if (!Commons.nullOrEmpty(m.getAccession())) {
					canonicalAccessions.add(m.getAccession());
					if (Commons.notNull(m.getIsoformPosition()))
						protCoords.add(new Coord.Prot(m.getAccession(), m.getIsoformPosition()));
				}
			});
			Set<Object[]> accPosSet = protCoords.stream().map(s -> s.toObjectArray()).collect(Collectors.toSet());

			final Map<String, List<Variation>> variationMap = options.contains(OPTIONS.POPULATION) ? variationFetcher.prefetchdb(accPosSet) : new HashedMap();

			options.parallelStream().forEach(o -> {
				if (o.equals(OPTIONS.FUNCTION))
					proteinsFetcher.prefetch(canonicalAccessions);
				//if (o.equals(OPTIONS.POPULATION))
				//	variationFetcher.prefetch(canonicalAccessionLocations);
			});

			// retrieve EVE scores
			Map<String, List<EVEScore>> eveScoreMap = protVarDataRepo.getEVEScores(accPosSet)
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
							ensgMappingList = mappingsConverter.createGenes(mappingList, gInput.getRef(), gInput.getAlt(), caddScore, eveScoreMap, variationMap, options);

						GenomeProteinMapping mapping = GenomeProteinMapping.builder().genes(ensgMappingList).build();
						gInput.getMappings().add(mapping);
					} catch (Exception ex) {
						gInput.getErrors().add("An exception occurred while processing this input");
						LOGGER.error(ex.getMessage());
					}
				});
			});

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

	public String inputSummary(List<UserInput> userInputs) {
		String inputSummary = String.format("Processed %d input%s ", userInputs.size(), FetcherUtils.pluralise(userInputs.size()));
		int[] counts = {0,0,0,0}; //genomic, coding, protein, ID
		List<String> invalidInputs = new ArrayList<>();


		userInputs.stream().forEach(input -> {
			if (input.getType() == Type.GENOMIC) counts[0]++;
			else if (input.getType() == Type.CODING) counts[1]++;
			else if (input.getType() == Type.PROTEIN) counts[2]++;
			else if (input.getType() == Type.ID) counts[3]++;

			if (!input.isValid())
				invalidInputs.add(String.format("Invalid input (%s): %s ", input.getInputStr(), Arrays.toString(input.getErrors().toArray())));
		});
		List<String> inputTypes = new ArrayList<>();
		if (counts[0] > 0) inputTypes.add(String.format("%d genomic", counts[0]));
		if (counts[1] > 0) inputTypes.add(String.format("%d cDNA", counts[1]));
		if (counts[2] > 0) inputTypes.add(String.format("%d protein", counts[2]));
		if (counts[3] > 0) inputTypes.add(String.format("%d ID", counts[3]));

		if (inputTypes.size() > 0) inputSummary += "(" + String.join(", ", inputTypes) + "). ";

		if (invalidInputs.size() > 0) {
			inputSummary += String.format("%d input%s %s not valid. ", invalidInputs.size(), FetcherUtils.pluralise(invalidInputs.size()), FetcherUtils.isOrAre(invalidInputs.size()));
			LOGGER.warn(Arrays.toString(invalidInputs.toArray()));
		}

		return inputSummary;
	}
}
