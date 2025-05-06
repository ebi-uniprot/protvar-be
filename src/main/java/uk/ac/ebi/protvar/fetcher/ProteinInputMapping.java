package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.protvar.converter.GeneConverter;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.Coord;
import uk.ac.ebi.protvar.model.data.*;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.repo.*;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.uniprot.domain.variation.Variant;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProteinInputMapping {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProteinInputMapping.class);
	private MappingRepo mappingRepo;
	private CaddPredictionRepo caddPredictionRepo;
	private AlleleFreqRepo alleleFreqRepo;
	private ScoreRepo scoreRepo;
	private PocketRepo pocketRepo;
	private InteractionRepo interactionRepo;
	private FoldxRepo foldxRepo;
	private ProteinsFetcher proteinsFetcher;
	private VariantFetcher variantFetcher;
	private GeneConverter geneConverter;

	/**
	 * Specialised and greatly simplified getMappings for genomic inputs only, by-passing many checks;
	 * used getMappingByAccession endpoint
	 * Differences
	 * 0. no need to groupInputs into type, may still need to filter out any invalid inputs(?) (in case incorrectly formed chr, pos, allele from db)
	 * 1. assembly param not needed
	 * 2. buildConversion not needed
	 * 3. id2Gen not needed
	 * 4. get refseq-uniprot accession mapping not needed
	 * 5. coding2Pro cDNA to protein inputs conversion not needed
	 * 6. pro2Gen protein to genomic inputs conversion not needed
	 *
	 * 7. a number of checks around instance of UserInput not needed as list will contain only genomic inputs
	 * 8. a number of checks around ref/alt allele not needed, including
	 * 	ERR_REF_ALLELE_EMPTY		ref and alt empty check
	 *	ERR_REF_ALLELE_MISMATCH		user input-UniProtseq ref mismatch check
	 *	ERR_VAR_ALLELE_EMPTY		alt empty check
	 *	ERR_REF_AND_VAR_ALLELE_SAME	ref and var same check
	 *
	 * Only that is needed is GenomicInput.getAlternates based on ref retrieved from db
	 *
	 *
	 * DB Call Summary per request:
	 * Note difference between DB calls/queries and Hikari connections.
	 * We're aiming to manage Hikari connection exhaustion if too many connections are opened.
	 *
	 * Before this method:
	 * - 1x: Get genomic coordinates for accession
	 *
	 * Within this method (always executed):
	 * - 1x: Get g2p mappings for chrPosList
	 * - 1x: Get CADD predictions for chrPosList
	 * - 1x: Get allele frequencies for chrPosList
	 *
	 * Minimum (no annotation: non-FUN, non-POP, non-STR):
	 * - 1x: Get AlphaMissense scores for accession
	 * => Total: 5 DB calls
	 *
	 * Maximum (all annotations enabled: FUN + POP + STR):
	 * - 1x [FUN]: Get conservation, EVE, ESM, and AlphaMissense scores for accession
	 * - 1x [FUN]: Get pockets for accession
	 * - 1x [FUN]: Get interactions for accession
	 * - 1x [FUN]: Get FoldX predictions for accession
	 * - 1x [POP]: Get variant (feature) map for accession
	 * => Total: 9 DB calls
	 *
	 * Notes:
	 * - Some calls (e.g., scores, pockets, interactions) are cached per accession.
	 * - All chrPosList-based queries are batched to minimize DB round trips.
	 * - ProteinsAPI call required if not cached.
	 * - PDBeAPI preloaded cache used for STR annotations.
	 *
	 * @param params
	 * @return
	 */
	@Transactional // So all DB calls inside this method share the same connection (to check)
	(readOnly = true) // Avoid unnecessary locks in the DB.
	public MappingResponse getMappings(String accession, InputParams params) {
		MappingResponse response = new MappingResponse(params.getInputs());

		// get all chrPos combination
		List<Object[]> chrPosList = new ArrayList<>();
		params.getInputs().stream().forEach(userInput -> {
			chrPosList.addAll(userInput.chrPos());
		});

		if (chrPosList.isEmpty()) {
			LOGGER.info("[{}] No chr-pos pairs found", accession);
			return response;
		}

		LOGGER.info("[{}] Starting data retrieval for {} chr-pos pairs", accession, chrPosList.size());

		List<GenomeToProteinMapping> g2pMappings = mappingRepo.getMappingsByChrPos(chrPosList);
		LOGGER.info("[{}] Retrieved {} g2p mappings", accession, g2pMappings.size());

		Map<String, List<CaddPrediction>> caddPredictionMap = caddPredictionRepo.getCADDByChrPos(chrPosList)
				.stream().collect(Collectors.groupingBy(CaddPrediction::getGroupBy));
		LOGGER.info("[{}] Retrieved {} CADD predictions", accession, caddPredictionMap.size());

		Map<String, List<AlleleFreq>> alleleFreqMap = alleleFreqRepo.getAlleleFreqs(chrPosList)
				.stream().collect(Collectors.groupingBy(AlleleFreq::getGroupBy));
		LOGGER.info("[{}] Retrieved {} allele freqs", accession, alleleFreqMap.size());

		// get all protein accessions and positions from retrieved mappings
		Set<String> canonicalAccessions = new HashSet<>();
		g2pMappings.stream().filter(GenomeToProteinMapping::isCanonical).forEach(m -> {
			if (!Commons.nullOrEmpty(m.getAccession())) {
				canonicalAccessions.add(m.getAccession());
			}
		});

		List<Score> scores = params.isFun() ? /* all scores */ scoreRepo.getScores(accession) : /* just AM scores */ scoreRepo.getAMScores(accession);
		Map<String, List<Score>> scoresMap = scores.stream().collect(Collectors.groupingBy(Score::getGroupBy));
		LOGGER.info("[{}] Retrieved {} scores groups", accession, scoresMap.size());

		Map<String, List<Pocket>> pocketsMap = new HashMap<>();
		Map<String, List<Interaction>> interactionsMap = new HashMap<>();
		Map<String, List<Foldx>> foldxsMap = new HashMap<>();
		Map<String, List<Variant>> variantsMap = new HashMap<>();

		if (params.isFun()) {
			pocketsMap.putAll(pocketRepo.getPockets(accession));
			LOGGER.info("[{}] Retrieved {} pockets groups", accession, pocketsMap.size());

			interactionsMap.putAll(interactionRepo.getInteractions(accession));
			LOGGER.info("[{}] Retrieved {} interactions groups", accession, interactionsMap.size());

			foldxsMap.putAll(foldxRepo.getFoldxs(accession));
			LOGGER.info("[{}] Retrieved {} foldxs groups", accession, foldxsMap.size());

			proteinsFetcher.prefetch(canonicalAccessions);
			LOGGER.info("[{}] Prefetched proteins for {} canonical accessions", accession, canonicalAccessions.size());
		}

		if (params.isPop()) {
			variantsMap.putAll(variantFetcher.getVariantMap(accession));
			LOGGER.info("[{}] Retrieved {} variants groups", accession, variantsMap.size());
		}

		Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
				.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

		LOGGER.info("[{}] Processing mapping for {} genomic inputs", accession, params.getInputs().size());
		params.getInputs().stream()
				.filter(UserInput::isValid)
				.map(i -> (GenomicInput) i)
				.forEach(gInput -> { // all inputs are genomic
			try {
				var key = gInput.groupByChrAndPos();
				var mappingList = map.get(key);
				var caddScores = caddPredictionMap.get(key);
				var alleleFreqs = alleleFreqMap.get(key);

				List<Gene> ensgMappingList = Collections.emptyList();

				if (mappingList != null && !mappingList.isEmpty()) {
					Set<String> altBases = GenomicInput.getAlternates(gInput.getRef());
					ensgMappingList = geneConverter.createGenes(
							mappingList, altBases, caddScores, alleleFreqs,
							scoresMap, variantsMap, pocketsMap, interactionsMap, foldxsMap,
							params);
				}

				gInput.getGenes().addAll(ensgMappingList);
			} catch (Exception ex) {
				gInput.getErrors().add("An exception occurred while processing this input");
				LOGGER.error("Error processing input {}: {}", gInput, ex.getMessage(), ex);
			}
		});
		return response;
	}

}
