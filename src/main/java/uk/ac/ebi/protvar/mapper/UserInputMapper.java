package uk.ac.ebi.protvar.mapper;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.converter.GeneConverter;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;
import uk.ac.ebi.protvar.input.mapper.Coding2Pro;
import uk.ac.ebi.protvar.input.mapper.ID2Gen;
import uk.ac.ebi.protvar.input.mapper.Pro2Gen;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.CaddPrediction;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.record.AccessionPosition;
import uk.ac.ebi.protvar.record.ArrayPair;
import uk.ac.ebi.protvar.record.ChromosomePosition;
import uk.ac.ebi.protvar.repo.CaddPredictionRepo;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.repo.ScoreRepo;
import uk.ac.ebi.protvar.repo.UniprotRefseqRepo;
import uk.ac.ebi.protvar.utils.Commons;

import java.util.*;
import java.util.stream.Collectors;

/**
 * User input mapper - handles mixed input types, thus needs
 * additional preprocess step for build conversion (if needed) and
 * map non-genomic input types to genomic coords (e.g. variant id, cdna->protein->genomic coords mapping)
 * before core g2p mapping and scores loading
 *
 * TODO consider if there is a need to have specialised (simplified) mapper for genomic- or protein-only inputs
 */
@Service
@RequiredArgsConstructor
public class UserInputMapper {
	private static final Logger LOGGER = LoggerFactory.getLogger(UserInputMapper.class);
	private final MappingRepo mappingRepo;
	private final CaddPredictionRepo caddPredictionRepo;
	private final ScoreRepo scoreRepo;
	private final UniprotRefseqRepo uniprotRefseqRepo;
	private final BuildProcessor buildProcessor;
	private final ID2Gen id2Gen;
	private final Coding2Pro coding2Pro;
	private final Pro2Gen pro2Gen;
	private final GeneConverter geneConverter;

	public void preprocess(List<UserInput> inputs, String requestAssembly, InputBuild detectedBuild) {
		Map<Type, List<UserInput>> groupedInputs = inputs.stream()
				.filter(UserInput::isValid)
				.collect(Collectors.groupingBy(UserInput::getType));
		// Build conversion for genomic + HGVSg 37 inputs
		buildProcessor.process(groupedInputs, requestAssembly, detectedBuild);

		// Variant ID mapping
		id2Gen.map(groupedInputs);

		// RefSeq-UniProt accession mapping
		Set<String> rsAccs = inputs.stream()
				.filter(input -> input instanceof HGVSp || input instanceof HGVSc)
				.map(input -> {
					String rsAcc = (input instanceof HGVSp)
							? ((HGVSp) input).getRsAcc()
							: ((HGVSc) input).getRsAcc();
					if (rsAcc != null && !rsAcc.isEmpty()) {
						int dotIdx = rsAcc.lastIndexOf(".");
						if (dotIdx != -1)
							rsAcc = rsAcc.substring(0, dotIdx);
						return rsAcc;
					}
					return null;
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		TreeMap<String, List<String>> rsAccsMap = uniprotRefseqRepo.getRefSeqNoVerUniprotMap(rsAccs);

		// cDNA to protein inputs conversion
		coding2Pro.convert(groupedInputs, rsAccsMap);

		// protein to genomic inputs conversion
		pro2Gen.convert(groupedInputs, rsAccsMap);
	}

	// TODO rename UserInput -> VariantInput
	public MappingResponse getMapping(List<UserInput> inputs, String requestAssembly,
									  InputBuild build, boolean multiFormat) {
		if (inputs == null || inputs.isEmpty())
			return new MappingResponse(List.of());

		MappingResponse response = new MappingResponse(inputs);

		// Preprocess mixed inputs
		if (multiFormat) preprocess(inputs, requestAssembly, build);

		MappingData core = loadCoreMappingAndScores(inputs);
		if (core == null) return response;

		//AnnotationData ann = preloadOptionalAnnotations(params, core);

		inputs.stream()
			.filter(UserInput::isValid)
			.forEach(input -> processInput(input, core));

		return response;
	}

	public MappingData loadCoreMappingAndScores(List<UserInput> inputs) {
		// Collect unique ChromosomePosition objects
		Set<ChromosomePosition> uniqueChrPos = inputs.stream()
				.flatMap(userInput -> userInput.getChrPosList().stream())
				.map(objArray -> new ChromosomePosition((String) objArray[0], (Integer) objArray[1]))
				.collect(Collectors.toSet());

		// Convert to aligned arrays from Set
		String[] chromosomes = uniqueChrPos.stream().map(ChromosomePosition::chromosome).toArray(String[]::new);
		Integer[] gpositions = uniqueChrPos.stream().map(ChromosomePosition::position).toArray(Integer[]::new);

		// Create the tuple
		ArrayPair<String, Integer> chrPosArrays = new ArrayPair<>(chromosomes, gpositions);

		if (chromosomes == null || chromosomes.length == 0)
			return null;

		// Core prediction and mapping
		var caddPredictionMap = caddPredictionRepo.getCADDByChrPos(chromosomes, gpositions)
				.stream().collect(Collectors.groupingBy(CaddPrediction::getVariantKey));

		var g2pMappings = mappingRepo.getMappingsByChrPos(chromosomes, gpositions);
		Map<String, List<GenomeToProteinMapping>> g2pMap = new HashMap<>();
		Set<String> canonicalAccessions = new HashSet<>();
		Set<AccessionPosition> uniqueProtCoords = new HashSet<>();

		g2pMappings.forEach(m -> {
			g2pMap.computeIfAbsent(m.getVariantKeyGenomic(), k -> new ArrayList<>()).add(m);

			if (m.isCanonical() && !Commons.nullOrEmpty(m.getAccession())) {
				canonicalAccessions.add(m.getAccession());
				if (Commons.notNull(m.getIsoformPosition())) {
					uniqueProtCoords.add(new AccessionPosition(m.getAccession(), m.getIsoformPosition()));
				}
			}
		});

		// Convert to aligned arrays for SQL unnest
		List<AccessionPosition> accPosList = new ArrayList<>(uniqueProtCoords);
		String[] accessions = accPosList.stream().map(AccessionPosition::accession).toArray(String[]::new);
		Integer[] ppositions = accPosList.stream().map(AccessionPosition::position).toArray(Integer[]::new);

		var accPosArrays = new ArrayPair<>(accessions, ppositions);

		var amScoreMap = scoreRepo.getMappingScores(accessions, ppositions)
				.stream().collect(Collectors.groupingBy(Score::getVariantKey));

		return new MappingData(chrPosArrays, g2pMap, caddPredictionMap, accPosArrays, canonicalAccessions, amScoreMap);
	}

	public void processInput(UserInput input, MappingData core) {
		input.getDerivedGenomicInputs().forEach(gInput -> {
			try {
				var chrPos = gInput.getVariantKey();
				var mappingList = core.getG2pMap().get(chrPos);
				var caddScores = core.getCaddMap().get(chrPos);

				List<Gene> ensgMappingList = Collections.emptyList();

				if (mappingList != null && !mappingList.isEmpty()) {
					String mappingRef = mappingList.get(0).getBaseNucleotide();
					Set<String> altBases = resolveAltBases(gInput, mappingRef);

					// if we have the alt base at this point, we can get the altCodon/AA
					/*Set<String> altCodons = altBases.stream()
							.map(mappingList.get(0)::getAltCodon)
							.collect(Collectors.toSet());*/

					ensgMappingList = geneConverter.createGenes(altBases, mappingList, caddScores, core.getAmScoreMap());
				}

				gInput.getGenes().addAll(ensgMappingList);
			} catch (Exception ex) {
				gInput.getErrors().add("An exception occurred while processing this input");
				LOGGER.error("Error processing input {}: {}", gInput, ex.getMessage(), ex);
			}
		});
	}

	private Set<String> resolveAltBases(GenomicInput gInput, String mappingRef) {
		String ref = gInput.getRef();
		String alt = gInput.getAlt();

		if (ref != null) {
			// Handle ref mismatch whether or not alt is provided
			if (!ref.equalsIgnoreCase(mappingRef)) {
				gInput.addWarning(String.format(ErrorConstants.ERR_REF_ALLELE_MISMATCH.toString(), ref, mappingRef));
				gInput.setRef(mappingRef);
			}

			// Case: Both ref and alt are provided (Y Y)
			if (alt != null) {
				if (alt.equalsIgnoreCase(mappingRef)) {
					gInput.addWarning(ErrorConstants.ERR_REF_AND_VAR_ALLELE_SAME);
					return GenomicInput.getAlternateBases(mappingRef);
				}
				return Set.of(alt);
			} // Case: Only ref is provided (Y X)
			else {
				gInput.addWarning(ErrorConstants.ERR_VAR_ALLELE_EMPTY);
				return GenomicInput.getAlternateBases(mappingRef);
			}
		}

		// Case: Both ref and alt are missing (X X)
		gInput.addWarning(ErrorConstants.ERR_REF_ALLELE_EMPTY);
		gInput.setRef(mappingRef);
		return GenomicInput.getAlternateBases(mappingRef);
	}

}
