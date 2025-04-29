package uk.ac.ebi.protvar.fetcher;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.converter.GeneConverter;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;
import uk.ac.ebi.protvar.input.mapper.Coding2Pro;
import uk.ac.ebi.protvar.input.mapper.ID2Gen;
import uk.ac.ebi.protvar.input.mapper.Pro2Gen;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.Coord;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.protvar.model.data.CaddPrediction;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.repo.*;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.uniprot.domain.variation.Variant;

import java.util.*;
import java.util.stream.Collectors;

/**
 *  CACHE/REPO                 CONTROLLER                     SERVICE                    FETCHER
 *
 *                             CoordinateMapping                                           MappingFetcher
 *                             POST mappings --------<input/s,a>------------------------->  -getMapping
 *                             GET  mapping                                              ^                    ^
 *                                                                                      |                       \
 *  InputCache                 PagedMapping                     PagedMappingSrv        |                         |
 *  -cacheFile/Text <--------- POST postInput                                         |  if "a" specified, use it|
 *          ^                       |-------------- <id,p,ps,a> --> -getInputResult  |   else use pre-determined |
 *           \                                     /                   |             |                           |
 *            \                GET  getResult ____/                    |            <id,inputs,a>                |
 *  -get <-----\-------<id>--------------------------------------------|____________/ (page of)inputs            |
 *               \                                                                       retrieved by id         |
 *                  \___       GET getResultByAcc--<acc,p,ps>-----> -getMappingByAccession                       |
 *  ProtVarDataRepo      \                                                  |                                    |
 *  -getGenInputsByAcc <--\-------------------------------------------------|----------> -getGenMappings         |
 *                         \                                                                                     |
 *                          \  Download                          DownloadSrv              CsvProcessor           |
 *                           \ -file (ID)           \                                     -process --------------
 *                             -text (ID)        --------------> -queueRequest             ID inputs=inputCache.get(inputId)
 *                             -input(ID|PROT|SING) /                                      PROT inputs=protVarDataRepo.getGenInputsByAcc
 *                                                                                         SING inputs=input
 * p: page
 * ps: pageSize
 * a: assembly
 * ann: annotations (fun, pop, str)
 * e: email
 * jn: jobName
 */
@Service
@RequiredArgsConstructor
public class CustomInputMapping {
	private static final Logger LOGGER = LoggerFactory.getLogger(CustomInputMapping.class);
	private final MappingRepo mappingRepo;

	private final CaddPredictionRepo caddPredictionRepo;

	private final ScoreRepo scoreRepo;

	private final AlleleFreqRepo alleleFreqRepo;

	private final UniprotRefseqRepo uniprotRefseqRepo;

	private final ID2Gen id2Gen;
	private final Pro2Gen pro2Gen;
	private final GeneConverter geneConverter;

	private final ProteinsFetcher proteinsFetcher;

	private final VariantFetcher variantFetcher;

	private final BuildProcessor buildProcessor;

	private final Coding2Pro coding2Pro;

	/**
	 * UI result display | API response (to align)
	 *
	 * --------------------
	 * |Mapping|Cadd|Score|      Fun      |   Pop    |  Str  -> PdbeStructureFetcher
	 * --------------------       |            \
	 *                            v             v
	 *                     ProteinsFetcher     VariantFetcher
	 *                     Pocket              AlleleFreq
	 *                     Interaction
	 *                     Foldx
	 *
	 */

	public MappingResponse getMapping(InputParams params) {
		if (params.getInputs() == null || params.getInputs().isEmpty())
			return new MappingResponse(List.of());

		MappingResponse response = new MappingResponse(params.getInputs());

		Map<Type, List<UserInput>> groupedInputs = params.getInputs().stream().filter(UserInput::isValid) // filter out any invalid inputs
				.collect(Collectors.groupingBy(UserInput::getType));

		buildProcessor.process(groupedInputs, params);

		// ID to genomic coords conversion
		id2Gen.map(groupedInputs);

		// get refseq-uniprot accession mapping
		Set<String> rsAccs = new HashSet<>();
		params.getInputs().stream().forEach(userInput -> {
			String rsAcc = null;
			if (userInput instanceof HGVSp)
				rsAcc = ((HGVSp) userInput).getRsAcc();
			else if (userInput instanceof HGVSc)
				rsAcc = ((HGVSc) userInput).getRsAcc();

			if (rsAcc != null && rsAcc.length() > 0) {
				int dotIdx = rsAcc.lastIndexOf(".");
				if (dotIdx != -1)
					rsAcc = rsAcc.substring(0, dotIdx);
				if (!rsAccs.contains(rsAcc))
					rsAccs.add(rsAcc);
			}
		});

		TreeMap<String, List<String>> rsAccsMap = uniprotRefseqRepo.getRefSeqNoVerUniprotMap(rsAccs);

		// cDNA to protein inputs conversion
		coding2Pro.convert(groupedInputs, rsAccsMap);

		// protein to genomic inputs conversion
		pro2Gen.convert(groupedInputs, rsAccsMap);

		// get all chrPos combination
		List<Object[]> chrPosList = new ArrayList<>();
		params.getInputs().stream().forEach(userInput -> {
			chrPosList.addAll(userInput.chrPos());
		});

		if (!chrPosList.isEmpty()) {

			// retrieve CADD predictions
			Map<String, List<CaddPrediction>> caddPredictionMap = caddPredictionRepo.getCADDByChrPos(chrPosList)
					.stream().collect(Collectors.groupingBy(CaddPrediction::getGroupBy));

			// retrieve allele freq
			Map<String, List<AlleleFreq>> alleleFreqMap = alleleFreqRepo.getAlleleFreqs(chrPosList)
					.stream().collect(Collectors.groupingBy(AlleleFreq::getGroupBy));

			// retrieve main mappings
			List<GenomeToProteinMapping> g2pMappings = mappingRepo.getMappingsByChrPos(chrPosList);

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
			List<Object[]> accPosList = protCoords.stream().map(s -> s.toObjectArray()).collect(Collectors.toList());

			Map<String, List<Score>>  scoreMap = scoreRepo.getScores(accPosList)
					.stream().collect(Collectors.groupingBy(Score::getGroupBy));
			final Map<String, List<Variant>> variantMap = params.isPop() ? variantFetcher.getVariantMap(accPosList) : new HashedMap();

			if (params.isFun())
				proteinsFetcher.prefetch(canonicalAccessions);

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			params.getInputs().stream()
					.filter(UserInput::isValid)
					.forEach(input -> {

				input.genInputs().forEach(gInput -> {
					try {
						var key = gInput.groupByChrAndPos();
						var mappingList = map.get(key);
						var caddScores = caddPredictionMap.get(key);
						var alleleFreqs = alleleFreqMap.get(key);

						List<Gene> ensgMappingList = Collections.emptyList();

						if (mappingList != null && !mappingList.isEmpty()) {
							String mappingRef = mappingList.get(0).getBaseNucleotide();
							Set<String> altBases = resolveAltBases(gInput, mappingRef);

							ensgMappingList = geneConverter.createGenes(
									mappingList, altBases, caddScores, alleleFreqs,
									scoreMap, variantMap, null, null, null,
									params);
						}

						gInput.getGenes().addAll(ensgMappingList);
					} catch (Exception ex) {
						gInput.getErrors().add("An exception occurred while processing this input");
						LOGGER.error("Error processing input {}: {}", gInput, ex.getMessage(), ex);
					}
				});
			});

		}
		return response;
	}

	private Set<String> resolveAltBases(GenomicInput gInput, String mappingRef) {
		if (gInput.getRef() == null && gInput.getAlt() == null) {
			gInput.addWarning(ErrorConstants.ERR_REF_ALLELE_EMPTY);
			gInput.setRef(mappingRef);
			return GenomicInput.getAlternates(mappingRef);
		}

		if (gInput.getRef() != null && !gInput.getRef().equalsIgnoreCase(mappingRef)) {
			gInput.addWarning(String.format(ErrorConstants.ERR_REF_ALLELE_MISMATCH.toString(), gInput.getRef(), mappingRef));
			gInput.setRef(mappingRef);
		}

		Set<String> altBases = GenomicInput.getAlternates(gInput.getRef());

		if (gInput.getAlt() == null) {
			gInput.addWarning(ErrorConstants.ERR_VAR_ALLELE_EMPTY);
		} else if (gInput.getAlt().equalsIgnoreCase(mappingRef)) {
			gInput.addWarning(ErrorConstants.ERR_REF_AND_VAR_ALLELE_SAME);
		} else {
			altBases = Set.of(gInput.getAlt());
		}

		return altBases;
	}

}
