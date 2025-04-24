package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

	private ScoreRepo scoreRepo;

	private AlleleFreqRepo alleleFreqRepo;

	private GeneConverter geneConverter;

	private ProteinsFetcher proteinsFetcher;

	private VariantFetcher variantFetcher;

	private PocketRepo pocketRepo;
	private InteractionRepo interactionRepo;
	private FoldxRepo foldxRepo;


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
	 * @param params
	 * @return
	 */
	public MappingResponse getMappings(String accession, InputParams params) {
		MappingResponse response = new MappingResponse(params.getInputs());

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

			Map<String, List<Score>> scoreMap = (params.isFun() ?
					// retrieve all scores
					scoreRepo.getScores(accession)
					: // retrieve just AM score
					scoreRepo.getAMScores(accession)).stream().collect(Collectors.groupingBy(Score::getGroupBy));

			final Map<String, List<Variant>> variantMap = params.isPop() ? variantFetcher.getVariantMap(accession) : new HashedMap();
			final Map<String, List<Pocket>> pocketsMap = params.isFun() ? pocketRepo.getPockets(accession) : null;
			final Map<String, List<Interaction>> interactionsMap = params.isFun() ? interactionRepo.getInteractions(accession) : null;
			final Map<String, List<Foldx>> foldxMap = params.isFun() ? foldxRepo.getFoldxs(accession) : null;

			if (params.isFun())
				proteinsFetcher.prefetch(canonicalAccessions);

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

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
								scoreMap, variantMap, pocketsMap, interactionsMap, foldxMap,
								params);
					}

					gInput.getGenes().addAll(ensgMappingList);
				} catch (Exception ex) {
					gInput.getErrors().add("An exception occurred while processing this input");
					LOGGER.error("Error processing input {}: {}", gInput, ex.getMessage(), ex);
				}
			});

		}
		return response;
	}

}
