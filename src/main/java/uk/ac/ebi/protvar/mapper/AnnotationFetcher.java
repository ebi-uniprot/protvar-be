package uk.ac.ebi.protvar.mapper;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.fetcher.VariantFetcher;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.response.StructureResidue;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.repo.*;
import uk.ac.ebi.protvar.service.FunctionalAnnService;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.uniprot.domain.variation.Variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This is used in two places
 * - AnnotationController for API calls (1 at a time, paged)
 * - DownloadProcessor for download requests with annotations (multiple at a time, can be paged or unpaged i.e. full download)
 *
 * It fetches annotations from various sources and builds the AnnotationData object.
 */
@Service
@RequiredArgsConstructor
public class AnnotationFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationFetcher.class);

	// functional info sources
	private final FunctionalAnnService functionalAnnService;
	private final PocketRepo pocketRepo;
	private final InteractionRepo interactionRepo;
	private final FoldxRepo foldxRepo;
	private final ScoreNewRepo scoreRepo; // annotation scores

	// population ann sources
	private final VariantFetcher variantFetcher;
	private final AlleleFreqRepo alleleFreqRepo;

	// structure sources
	private final StructureService structureService;

	/*
	Before:
	- 1x [InputHandler]: Get genomic variant input

	Core mappings DB calls:
	- 1x [G2P]: GenomeToProteinMapping
	- 1x [CADD]: CaddPrediction
	- 1x [AM]: AM scores

	DB calls per function call:
	Maximum (all annotations enabled: FUN + POP + STR):
	- 5x [FUN]: FunInfo, Scores, pockets, interactions & foldxs
	- 2x [POP]: Variant (feature) & allele freq map
	- 1x [STR]: Structure
	=> min 1, max 8 DB calls

	TO check: which calls can be/is cached
	 */
	public AnnotationData preloadOptionalAnnotations(MappingData core, boolean fun, boolean pop, boolean str) {
		Map<String, List<Variant>> variantMap = Map.of();
		Map<String, List<AlleleFreq>> freqMap = Map.of();

		Map<String, List<Score>> scoreMap = Map.of();
		Map<String, List<Pocket>> pocketMap = Map.of();
		Map<String, List<Interaction>> interactMap = Map.of();
		Map<String, List<Foldx>> foldxMap = Map.of();

		// TODO(revisit): for single-accession + whole-protein downloads we
		// could collapse the per-(accession,position) array queries below
		// into one `WHERE accession=?` query per repo. Detection requires
		// knowing the download is single-accession at this point — left as
		// a follow-up; for now the array path is the universal default.

		String[] accessions = core.getAccPosArrays().first();
		Integer[] positions = core.getAccPosArrays().second();

		if (fun) {
			LOGGER.info("Preloading protein function annotation for {} canonical accessions", core.getCanonicalAccessions().size());
			functionalAnnService.preloadFunctionCache(core.getCanonicalAccessions());

			scoreMap = scoreRepo.getAnnotationScores(accessions, positions) // non-AM scores
					.stream().collect(Collectors.groupingBy(Score::getVariantKey));
			pocketMap = pocketRepo.getPockets(accessions, positions);
			interactMap = interactionRepo.getInteractions(accessions, positions);
			foldxMap = foldxRepo.getFoldxs(accessions, positions);
		}

		if (pop) {
			variantMap = variantFetcher.getVariantMap(accessions, positions);
			freqMap = alleleFreqRepo.getAlleleFreqs(core.getChrPosArrays().first(), core.getChrPosArrays().second())
					.stream().collect(Collectors.groupingBy(AlleleFreq::getVariantKey));
		}

		if (str) {
			structureService.preloadStructureCache(new ArrayList<>(core.getCanonicalAccessions()));
		}

		return AnnotationData.builder()
				.fun(fun).pop(pop).str(str)
				.variantMap(variantMap)
				.freqMap(freqMap)
				.scoreMap(scoreMap)
				.pocketMap(pocketMap)
				.interactMap(interactMap)
				.foldxMap(foldxMap)
				.build();
	}

	public List<StructureResidue> buildStructure(String accession, int position) {
		return structureService.getStr(accession, position);
	}
	public AnnotationData getAPIFunctionalData(String accession, Integer position, String variantAA) {
		Map<String, List<Pocket>> pocketMap = pocketRepo.getPockets(accession, position);
		Map<String, List<Interaction>> interactMap = interactionRepo.getInteractions(accession, position);
		Map<String, List<Foldx>> foldxMap = foldxRepo.getFoldxs(accession, position, AminoAcid.oneLetter(variantAA));

		List<Score> scores = scoreRepo.getAnnotationScores(new String[]{accession}, new Integer[]{position});
		Map<String, List<Score>> scoreMap = scores.stream()
				.collect(Collectors.groupingBy(Score::getVariantKey));

		return AnnotationData.builder()
				.pocketMap(pocketMap)
				.interactMap(interactMap)
				.foldxMap(foldxMap)
				.scoreMap(scoreMap)
				.build();
	}

	public AnnotationData getAPIPopulationData(String accession, Integer position,
											   String chromosome, Integer genomicPosition) {
		Map<String, List<Variant>> variantMap = variantFetcher.getVariants(accession, position);
		Map<String, List<AlleleFreq>> freqMap = null;

		if (chromosome != null && genomicPosition != null) {
			freqMap = alleleFreqRepo.getAlleleFreqs(new String[]{chromosome}, new Integer[]{genomicPosition})
					.stream().collect(Collectors.groupingBy(AlleleFreq::getVariantKey));
		}

		return AnnotationData.builder()
				.variantMap(variantMap)
				.freqMap(freqMap)
				.build();
	}
}