package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.format.genomic.Gnomad;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.format.genomic.VCF;
import uk.ac.ebi.protvar.input.mapper.Coding2Pro;
import uk.ac.ebi.protvar.input.mapper.ID2Gen;
import uk.ac.ebi.protvar.input.mapper.Pro2Gen;
import uk.ac.ebi.protvar.input.processor.BuildConversion;
import uk.ac.ebi.protvar.input.processor.InputProcessor;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.Coord;
import uk.ac.ebi.protvar.model.data.CADDPrediction;
import uk.ac.ebi.protvar.model.data.EVEScore;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.Commons;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MappingFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(MappingFetcher.class);

	private InputProcessor inputProcessor;
	private ProtVarDataRepo protVarDataRepo;

	private ID2Gen id2Gen;
	private Pro2Gen pro2Gen;
	private Mappings2GeneConverter mappingsConverter;

	private ProteinsFetcher proteinsFetcher;

	private VariationFetcher variationFetcher;

	private BuildConversion buildConversion;

	private Coding2Pro coding2Pro;

	private MappingResponse initMappingResponse(List<UserInput> userInputs) {
		MappingResponse response = new MappingResponse();
		List<Message> messages = new ArrayList<>();
		response.setMessages(messages);
		response.setInputs(userInputs);
		String inputSummary = inputProcessor.summary(userInputs);
		messages.add(new Message(Message.MessageType.INFO, inputSummary));
		return response;
	}

	/**
	 *
	 * @param userInputs is list of user inputs
	 * @return MappingResponse
	 */
	public MappingResponse getMappings(List<UserInput> userInputs, List<OPTIONS> options, String assemblyVersion) {
		MappingResponse response = initMappingResponse(userInputs);

		Map<Type, List<UserInput>> groupedInputs = userInputs.stream().filter(UserInput::isValid) // filter out any invalid inputs
				.collect(Collectors.groupingBy(UserInput::getType));

		buildConversion.process(groupedInputs, assemblyVersion, response);

		// ID to genomic coords conversion
		id2Gen.convert(groupedInputs);

		// cDNA to protein inputs conversion
		coding2Pro.convert(groupedInputs);

		// protein to genomic inputs conversion
		pro2Gen.convert(groupedInputs);

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
						List<GenomeToProteinMapping> mappingList = map.get(gInput.groupByChrAndPos());
						List<CADDPrediction> caddScores = predictionMap.get(gInput.groupByChrAndPos());

						List<Gene> ensgMappingList;

						if (mappingList == null || mappingList.isEmpty()) {
							ensgMappingList = new ArrayList<>();
						} else {

							Set<String> altBases = new HashSet<>();
							if (gInput.getAlt() != null)
								altBases.add(gInput.getAlt());

							if (input instanceof GenomicInput || input instanceof VCF ||
									input instanceof Gnomad || input instanceof HGVSg) {

								String refBase = mappingList.get(0).getBaseNucleotide();

								// TODO
								// for genomic input,
								// if no base provided, use db ref + 3 alt bases
								// if both ref and alt provided, check ref equals db ref
								// if only one base provided, check if base equals ref,
								//    if yes, use 3 alt bases i.e. add new genomic input to results
								//    if not, assume base provided is alt, use db ref as ref

								if (gInput.getRef() == null && gInput.getAlt() == null) {
									gInput.setRef(refBase);
									altBases = GenomicInput.VALID_ALLELES.stream().filter(b -> !b.equals(refBase)).collect(Collectors.toSet());
								} else if (gInput.getRef() != null && gInput.getAlt() != null) {

									if (!gInput.getRef().equalsIgnoreCase(refBase)) {
										gInput.addWarning("Reference allele at position incorrect.");
										gInput.setRef(refBase);
									}
								} else {
									if (gInput.getRef().equalsIgnoreCase(refBase)) {
										altBases = GenomicInput.VALID_ALLELES.stream().filter(b -> !b.equals(refBase)).collect(Collectors.toSet());
									} else {
										gInput.addWarning("Reference allele at position incorrect.");
										gInput.setAlt(gInput.getRef());
										gInput.setRef(refBase);
									}
								}
							}
							ensgMappingList = mappingsConverter.createGenes(mappingList, gInput, altBases, caddScores, eveScoreMap, variationMap, options);
						}

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

}
