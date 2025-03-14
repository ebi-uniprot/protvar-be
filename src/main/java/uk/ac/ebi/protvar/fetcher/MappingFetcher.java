package uk.ac.ebi.protvar.fetcher;

import lombok.AllArgsConstructor;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.converter.Mappings2GeneConverter;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.genomic.Gnomad;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.format.genomic.VCF;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;
import uk.ac.ebi.protvar.input.mapper.Coding2Pro;
import uk.ac.ebi.protvar.input.mapper.ID2Gen;
import uk.ac.ebi.protvar.input.mapper.Pro2Gen;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.Coord;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.protvar.model.data.CADDPrediction;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.repo.*;
import uk.ac.ebi.protvar.utils.Commons;

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
 *                          \  Download                          DownloadSrv              CSVDataFetcher         |
 *                           \ -file (ID)           \                                     -writeCSVResult -------
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
@AllArgsConstructor
public class MappingFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(MappingFetcher.class);
	private MappingRepo mappingRepo;

	private PredictionRepo predictionRepo;

	private ScoreRepo scoreRepo;

	private AlleleFreqRepo alleleFreqRepo;

	private UniprotRefseqRepo uniprotRefseqRepo;

	private ID2Gen id2Gen;
	private Pro2Gen pro2Gen;
	private Mappings2GeneConverter mappingsConverter;

	private ProteinsFetcher proteinsFetcher;

	private VariationFetcher variationFetcher;

	private BuildProcessor buildProcessor;

	private Coding2Pro coding2Pro;

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
			Map<String, List<CADDPrediction>> predictionMap = predictionRepo.getCADDByChrPos(chrPosList)
					.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

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

			final Map<String, List<Variation>> variationMap = params.isPop() ? variationFetcher.prefetchdb(accPosList) : new HashedMap();

			if (params.isFun())
				proteinsFetcher.prefetch(canonicalAccessions);

			// retrieve AA scores
			Map<String, List<Score>>  scoreMap = scoreRepo.getScores(accPosList)
					.stream().collect(Collectors.groupingBy(Score::getGroupBy));

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			params.getInputs().stream().filter(UserInput::isValid).forEach(input -> {

				List<GenomicInput> gInputs = new ArrayList<>();
				gInputs.addAll(input.genInputs());

				gInputs.forEach(gInput -> {
					try {
						List<GenomeToProteinMapping> mappingList = map.get(gInput.groupByChrAndPos());
						List<CADDPrediction> caddScores = predictionMap.get(gInput.groupByChrAndPos());
						List<AlleleFreq> alleleFreqs = alleleFreqMap.get(gInput.groupByChrAndPos());

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

								if (gInput.getRef() == null && gInput.getAlt() == null) {
									gInput.addWarning(ErrorConstants.ERR_REF_ALLELE_EMPTY);
									gInput.setRef(refBase);
									altBases = GenomicInput.getAlternates(refBase);
								} else if (gInput.getRef() != null) {

									if (!gInput.getRef().equalsIgnoreCase(refBase)) {
										gInput.addWarning(
												String.format(ErrorConstants.ERR_REF_ALLELE_MISMATCH.toString(),
														gInput.getRef(),
														refBase));
										gInput.setRef(refBase);
									}

									altBases = GenomicInput.getAlternates(gInput.getRef());

									if (gInput.getAlt() == null) {
										gInput.addWarning(ErrorConstants.ERR_VAR_ALLELE_EMPTY);
									} else {
										// alt should not be same as ref
										if (gInput.getAlt().equalsIgnoreCase(refBase)) {
											gInput.addWarning(ErrorConstants.ERR_REF_AND_VAR_ALLELE_SAME);
											// use all alt bases
										} else {
											altBases = Set.of(gInput.getAlt());
										}
									}

								}
							}
							ensgMappingList = mappingsConverter.createGenes(mappingList, gInput, altBases, caddScores, alleleFreqs, scoreMap, variationMap, params);
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
	public MappingResponse getGenMappings(InputParams params) {
		MappingResponse response = new MappingResponse(params.getInputs());

		// get all chrPos combination
		List<Object[]> chrPosList = new ArrayList<>();
		params.getInputs().stream().forEach(userInput -> {
			chrPosList.addAll(userInput.chrPos());
		});

		if (!chrPosList.isEmpty()) {

			// retrieve CADD predictions
			Map<String, List<CADDPrediction>> predictionMap = predictionRepo.getCADDByChrPos(chrPosList)
					.stream().collect(Collectors.groupingBy(CADDPrediction::getGroupBy));

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

			final Map<String, List<Variation>> variationMap = params.isPop() ? variationFetcher.prefetchdb(accPosList) : new HashedMap();

			if (params.isFun())
				proteinsFetcher.prefetch(canonicalAccessions);

			// retrieve AA scores
			Map<String, List<Score>> scoreMap = scoreRepo.getScores(accPosList)
					.stream().collect(Collectors.groupingBy(Score::getGroupBy));

			Map<String, List<GenomeToProteinMapping>> map = g2pMappings.stream()
					.collect(Collectors.groupingBy(GenomeToProteinMapping::getGroupBy));

			params.getInputs().stream().filter(UserInput::isValid).map(i -> (GenomicInput) i).forEach(gInput -> { // all inputs are genomic

				try {
					List<GenomeToProteinMapping> mappingList = map.get(gInput.groupByChrAndPos());
					List<CADDPrediction> caddScores = predictionMap.get(gInput.groupByChrAndPos());
					List<AlleleFreq> alleleFreqs = alleleFreqMap.get(gInput.groupByChrAndPos());

					List<Gene> ensgMappingList;

					if (mappingList == null || mappingList.isEmpty()) {
						ensgMappingList = new ArrayList<>();
					} else {
						Set<String> altBases = GenomicInput.getAlternates(gInput.getRef());
						ensgMappingList = mappingsConverter.createGenes(mappingList, gInput, altBases, caddScores, alleleFreqs, scoreMap, variationMap, params);
					}

					GenomeProteinMapping mapping = GenomeProteinMapping.builder().genes(ensgMappingList).build();
					gInput.getMappings().add(mapping);
				} catch (Exception ex) {
					gInput.getErrors().add("An exception occurred while processing this input");
					LOGGER.error(ex.getMessage());
				}
			});

		}
		return response;
	}

}
