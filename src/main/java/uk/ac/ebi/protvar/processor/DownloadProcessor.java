package uk.ac.ebi.protvar.processor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.controller.DownloadController;
import uk.ac.ebi.protvar.fetcher.csv.CsvFunctionDataBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CsvPopulationDataBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CsvStructureDataBuilder;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.mapper.AnnotationData;
import uk.ac.ebi.protvar.mapper.AnnotationFetcher;
import uk.ac.ebi.protvar.mapper.MappingData;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.service.DownloadStatusService;
import uk.ac.ebi.protvar.service.MappingService;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.service.UploadCacheService;
import uk.ac.ebi.protvar.service.HeavyDbWork;
import uk.ac.ebi.protvar.service.UploadFilterService;
import uk.ac.ebi.protvar.service.InputService;
import uk.ac.ebi.protvar.model.score.AmScore;
import uk.ac.ebi.protvar.model.score.PopEveScore;
import uk.ac.ebi.protvar.processor.CsvHeaders.Group;
import uk.ac.ebi.protvar.types.Assembly;
import uk.ac.ebi.protvar.types.CuratedCategory;
import uk.ac.ebi.protvar.types.KnownSource;
import uk.ac.ebi.protvar.types.PopEveClass;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.UploadVariantRepo.Passing;
import uk.ac.ebi.protvar.utils.*;

/**
 * Handles CSV download requests received from RabbitMQ.
 *
 * - Requests run inline on the RabbitMQ listener thread (concurrency=5, prefetch=1).
 * - Large jobs are split into partitions (csv.partition.size inputs), processed in HeavyDbWork slots: the
 *   budget shared with upload builds, so downloads and uploads together stay within it.
 * - Each partition streams results to CSV, merged and zipped after all parts complete.
 *
 * Limits:
 * - Max concurrent jobs: 5 (RabbitMQ listener concurrency)
 * - Max concurrent DB-hitting partitions: 10 (via semaphore)
 * - Small job (≤1000 inputs): 1 task, 1 DB call group (5–9 queries).
 * - Large job (~30,000 inputs): ~30 partitions, up to 10 parallel at a time.
 * - Total DB usage remains below Hikari max (100 connections).
 *
 * DB call breakdown for protein download:
 * - 1x call: get genomic coords for accession
 * - 4–8x calls: mapping queries PER PARTITION
 * Example: protein P22304
 *   → 550 AA → 1650 genomic coords
 *   → Partitioned into 1000 + 650 → 2 partitions
 *   → Total DB calls = 1 + (2 × 4–8) = 9–17 calls
 *
 * RabbitMQ prefetch=1 ensures one unacked message per consumer for safe backpressure.
 * Executors have bounded queues to avoid memory saturation.
 */
@Service
@RequiredArgsConstructor
public class DownloadProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(DownloadProcessor.class);
	private static final String NO_MAPPING = "No mapping found";
	private final HeavyDbWork heavyDbWork;
	private final CsvFunctionDataBuilder csvFunctionDataBuilder;
	private final CsvPopulationDataBuilder csvPopulationDataBuilder;
	private final CsvStructureDataBuilder csvStructureDataBuilder;
	private final InputService inputService;
	private final UploadCacheService uploadCacheService;
	private final UploadFilterService uploadFilterService;
	private final MappingService mappingService;
	private final InputMapper inputMapper;
	private final AnnotationFetcher annotationFetcher;
	private final StructureService structureService;
	private final DownloadStatusService downloadStatusService;
	@Value("${app.data.folder}")
	private String dataFolder;
	@Value("${app.tmp.folder}")
	private String tmpFolder;
	@Value("${csv.partition.size:4000}")
	private int chunkSize;
	// Cap on processing attempts per job. When the listener uses manual ack,
	// a JVM crash mid-job leaves the message unacked and Rabbit redelivers it.
	// Without a cap, a poison payload that reliably crashes the BE would loop
	// forever and brick the queue.
	@Value("${app.download.max-attempts:3}")
	private int maxAttempts;

	// Each partition (chunk) holds at most 1 Hikari connection at a time:
	// loadCoreMappingAndScores is @Transactional(readOnly), so its multi-query
	// burst shares one connection. The remaining DB calls in the partition
	// (preprocess, optional annotations, structure lookup) happen sequentially
	// and each borrows + returns. Semaphore=10 caps in-flight partitions per
	// job, keeping overall connection count below the Hikari pool max.
	public void process(DownloadRequest request) {
		String id = request.getFname();
		long start = System.currentTimeMillis();

		DownloadStatus current = downloadStatusService.get(id);
		DownloadState currentState = current != null ? current.getState() : null;

		// Idempotency guard: a redelivery after a terminal state shouldn't redo work.
		if (currentState == DownloadState.READY || currentState == DownloadState.FAILED) {
			LOGGER.info("[{}] Skipping redelivery; already {}", id, currentState);
			return;
		}

		// Redelivery from a crashed prior attempt: the previous worker set
		// state=PROCESSING but never reached a terminal state, so manual ack
		// didn't fire and Rabbit re-handed us the message.
		int previousAttempts = (currentState == DownloadState.PROCESSING && current.getAttempts() != null)
				? current.getAttempts() : 0;
		int attempts = previousAttempts + 1;

		if (attempts > maxAttempts) {
			LOGGER.error("[{}] Aborting after {} crashed attempts", id, previousAttempts);
			downloadStatusService.markFailed(id, DownloadStatusService.MSG_RETRIES_EXHAUSTED);
			Email.notifyDevErr(request, List.of(), new RuntimeException(
					"Download " + id + " gave up after " + previousAttempts + " crashed attempts"));
			return;
		}

		LOGGER.info("[{}] Download request started (attempt {}/{}) for {}", id, attempts, maxAttempts, request.inputRef());
		downloadStatusService.markProcessing(id, attempts);
		try {
			Path zipPath = Path.of(dataFolder, id + ".csv.zip");
			if (Files.exists(zipPath)) {
				LOGGER.warn("Download file already exists: {}", zipPath);
				downloadStatusService.markReady(id, fileSize(zipPath));
				return;
			}

			// detectBuild must run before mapping for resultId requests so
			// that the cached InputBuild is populated when we read it below.
			if (request.getResultId() != null && !request.getResultId().isBlank()) {
				inputService.detectBuild(InputRequest.builder()
						.inputId(request.getResultId())
						.assembly(request.getAssembly())
						.build());
			}

			handleDownload(request, zipPath);
			downloadStatusService.markReady(id, fileSize(zipPath));
			Email.notifyUser(request);
		} catch (DownloadTooLargeException e) {
			LOGGER.warn("Download {} exceeded the row cap during processing", id);
			downloadStatusService.markFailed(id, DownloadStatusService.MSG_TOO_LARGE);
		} catch (Exception e) {
			downloadStatusService.markFailed(id, DownloadStatusService.MSG_PROCESSING_FAILED);
			handleException(e, request, List.of());
		}
		long end = System.currentTimeMillis();
		long durationMs = end - start;

		LOGGER.info("[{}] Download request completed in {}", id, formatDuration(durationMs));
	}

	private long fileSize(Path path) {
		try {
			return Files.exists(path) ? Files.size(path) : 0L;
		} catch (IOException e) {
			LOGGER.warn("Failed to read size of {}: {}", path, e.getMessage());
			return 0L;
		}
	}

	private void handleDownload(DownloadRequest request, Path zipPath) throws Exception {
		Path csvPath = Path.of(tmpFolder, request.getFname() + ".csv");
		CsvLayout layout = CsvLayout.of(request);

		InputBuild build = (request.getResultId() != null && !request.getResultId().isBlank())
				? uploadCacheService.getBuild(request.getResultId()) : null;

		if (!Boolean.TRUE.equals(request.getFull())) {
			LOGGER.info("Page download request: {}", request.getFname());
			if (UploadFilterService.applies(request)) {
				UploadFilterService.Result filtered = uploadFilterService.get(request, build);
				if (filtered.preparing() != null)
					throw new IllegalStateException("Upload " + request.getResultId() + " is not prepared for filtering");
				processAndWriteCsv(filtered.page().getContent(), filtered.passing(), csvPath, request.getAssembly(), build, layout, true);
			} else {
				List<VariantInput> inputs = mappingService.getInputs(request).getContent();
				processAndWriteCsv(inputs, null, csvPath, request.getAssembly(), build, layout, true);
			}
		} else {
			LOGGER.info("Full download request: {}", request.getFname());
			processFullDownload(request, csvPath, build, layout);
		}

		FileUtils.zipFile(csvPath, zipPath);
		Files.deleteIfExists(csvPath);
	}

	// passing: for a filtered upload, the rows that passed (others are trimmed); null otherwise
	private void processAndWriteCsv(List<VariantInput> inputs, Set<Passing> passing, Path outputPath, String assembly,
                                    InputBuild build, CsvLayout layout, boolean writeHeader) throws Exception {
		inputMapper.preprocess(inputs, assembly, build);
		addDetectedBuildNote(inputs, assembly, build);

		MappingData coreMapping = inputMapper.loadCoreMappingAndScores(inputs);
		if (coreMapping != null) {
			AnnotationData annData = annotationFetcher.preloadOptionalAnnotations(coreMapping,
					layout.has(Group.FUNCTION), layout.has(Group.PREDICTION), layout.has(Group.POPULATION),
					layout.has(Group.STRUCTURE), layout.keeps("Known_in") || layout.keeps("UniProt_curated"));

			try (Stream<String[]> rows = streamInputsToCsv(inputs, passing, coreMapping, annData, layout)) {
				writeCsv(outputPath, rows, layout, writeHeader);
			}
		} else {
			// No genomic coordinates (e.g. all inputs invalid or failed GRCh37
			// conversion): still write one row per invalid input, as the web shows.
			writeCsv(outputPath, inputs.stream().filter(i -> !i.isValid()).map(i -> getCsvDataInvalidInput(i, layout)),
					layout, writeHeader);
		}
	}

	// AUTO requests: note the detected (or assumed) assembly (PV-501–503) on genomic inputs whose
	// build isn't implied by the input itself (RefSeq HGVS g. is excluded), as the web shows it.
	private void addDetectedBuildNote(List<VariantInput> inputs, String assembly, InputBuild build) {
		if (!Assembly.autodetect(assembly) || build == null || build.getMessage() == null)
			return;
		inputs.stream()
				.filter(i -> i instanceof GenomicInput g && g.getRefseqId() == null)
				.forEach(i -> i.getMessages().add(build.getMessage()));
	}

	// Process in parallel, partitioning the input into chunks
	//
	// TODO(redesign): full download flattens to CSV, so the per-chunk
	// MappingData hierarchy built by inputMapper.loadCoreMappingAndScores
	// (g2pMap + genomicScoreMap + scoreMap + canonical/accPos sets) is overhead.
	// Read the rows with their scores straight from coding_variant (as CodingVariantRepo does)
	// and stream them to CSVWriter via RowCallbackHandler.
	// The API page path keeps loadCoreMappingAndScores (page-bounded JSON
	// output genuinely benefits from the in-memory hierarchy).
	private void processFullDownload(DownloadRequest request, Path csvPath,
									 InputBuild build, CsvLayout layout) throws Exception {
		AtomicInteger chunkIndex = new AtomicInteger(0);
		HeavyDbWork.Job job = heavyDbWork.job("download " + request.getFname());
		List<Future<Path>> futures = new ArrayList<>();
		List<Path> partPaths = new ArrayList<>();   // tracked here so we can clean up on failure
		long totalStreamed = 0;
		boolean success = false;
		try {
			Stream<UploadFilterService.Result> chunkStream = UploadFilterService.applies(request)
					? uploadFilterService.chunks(request, chunkSize)
					: mappingService.streamChunkedInputs(request, chunkSize).map(c -> new UploadFilterService.Result(new PageImpl<>(c), null, null));
			try (chunkStream) {
				for (UploadFilterService.Result result : (Iterable<UploadFilterService.Result>) chunkStream::iterator) {
					List<VariantInput> chunk = result.page().getContent();
					totalStreamed += chunk.size();
					if (totalStreamed > DownloadController.MAX_FULL_DOWNLOAD_ROWS) {
						throw new DownloadTooLargeException();
					}
					int chunkNum = chunkIndex.getAndIncrement();
					Path partPath = Path.of(tmpFolder, request.getFname() + "_" + chunkNum + ".csv");
					partPaths.add(partPath);
					// blocks while this download already has its share of slots in use
					futures.add(job.submit(() -> {
						LOGGER.info("[{}] Processing chunk #{}", request.getFname(), chunkNum);
						processAndWriteCsv(chunk, result.passing(), partPath, request.getAssembly(), build, layout, false);
						return partPath;
					}));
				}
			}

			// Wait for all CSV parts to be written
			List<Path> csvParts = new ArrayList<>();
			for (Future<Path> future : futures) {
				csvParts.add(future.get()); // blocking wait
			}

			// Merge all parts
			mergeCsvFiles(csvParts, csvPath, layout);
			success = true;
			LOGGER.info("[{}] {} chunks of {}; waited for slots {} ms", request.getFname(), csvParts.size(), chunkSize,
					job.queuedMs());
		} finally {
			if (!success) {
				// Drain any in-flight futures so workers release their slots
				// and finish writing their part files before we delete them.
				for (Future<Path> future : futures) {
					if (!future.isDone()) {
						try { future.get(); } catch (Exception ignored) {}
					}
				}
				for (Path part : partPaths) {
					try { Files.deleteIfExists(part); }
					catch (IOException e) { LOGGER.warn("Could not delete part file {}: {}", part, e.getMessage()); }
				}
			}
		}
	}

	private void handleException(Exception e, DownloadRequest request, List<String> inputs) {
		if (e instanceof CannotGetJdbcConnectionException) { // skip printing stack trace
			LOGGER.error("DB connection failure for request {}: {}", request.getFname(), e.getClass().getSimpleName());
		} else {
			LOGGER.error("Processing failed for {}: {}", request.getFname(), e.getMessage(), e);
		}
		Email.notifyUserErr(request, inputs);
		Email.notifyDevErr(request, inputs, e);
	}

	private void writeCsv(Path file, Stream<String[]> rowsStream, CsvLayout layout, boolean writeHeader) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(file))) {
			// Write the header first if necessary
			if (writeHeader) {
				writer.writeNext(layout.header());
			}
			// Write the data rows using stream
			rowsStream.forEach(writer::writeNext);
		}
	}

	private void mergeCsvFiles(List<Path> csvFiles, Path mergedFile, CsvLayout layout) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(mergedFile))) {
			// Write the header first
			writer.writeNext(layout.header());

			for (Path csvFile : csvFiles) {
				try (CSVReader reader = new CSVReader(Files.newBufferedReader(csvFile))) {
					String[] values;
					// Read and write each line from the current CSV file
					while ((values = reader.readNext()) != null) {
						writer.writeNext(values);
					}
				} catch (CsvValidationException e) {
					throw new IOException("Error reading CSV file: " + csvFile, e);
				}
			}
		}
	}

	// Stream the inputs and expand them based on input types
	private Stream<String[]> streamInputsToCsv(List<VariantInput> inputs,
											   Set<Passing> passing,
											   MappingData coreMapping,
												AnnotationData annData,
												CsvLayout layout) {
		// Stream the inputs instead of collecting them into a list
		Stream.Builder<String[]> builder = Stream.builder();
		inputs.stream()
			.forEach(input -> {
				if (!input.isValid()) {
					// Invalid input, add one row
					builder.add(getCsvDataInvalidInput(input, layout));
					return;
				}
				// Process the input and generate CSV rows
				inputMapper.processInput(input, coreMapping);
				if (passing != null) uploadFilterService.trim(List.of(input), passing);
				input.getDerivedGenomicVariants()
						.forEach(genomicVariant -> generateGenomicCsvRows(builder, input, genomicVariant, annData, layout));
		});

		return builder.build();
	}

	private void generateGenomicCsvRows(Stream.Builder<String[]> builder,
										VariantInput input,
										GenomicVariant genomicVariant,
										AnnotationData annData,
										CsvLayout layout) {
		String messages = input.getMessages().stream().map(Message::toString)
				.filter(msg -> !msg.isEmpty()) // Filter for non-empty messages
				.collect(Collectors.joining(";"));
		final String notes = messages.isEmpty() ? Constants.NA : messages;

		var genes = genomicVariant.getGenes();
		if (genes.isEmpty())
			builder.add(getCsvDataMappingNotFound(input, genomicVariant, layout));
		else
			genes.forEach(gene -> builder.add(getCsvData(notes, gene, input, genomicVariant, annData, layout)));
	}

	String idValue(VariantInput input) {
		return input.getType() == VariantType.VARIANT_ID ? input.getInputStr() :
				(input.getFormat() == VariantFormat.VCF ? ((GenomicInput) input).getId() : null);
	}

	// Variant group values: Input, Notes, ID, Chromosome, Position, Ref, Alt, Known_in, UniProt_curated
	private List<String> variantValues(VariantInput input, String notes, String chr, Integer pos, String ref, String alt,
									   String knownIn, String curated) {
		return Arrays.asList(input.getInputStr(), notes, idValue(input), chr, pos == null ? null : pos.toString(),
				ref, alt, knownIn, curated);
	}

	// A row for a variant with no mapping
	String[] getCsvDataMappingNotFound(VariantInput input, GenomicVariant genomicVariant, CsvLayout layout) {
		return layout.row(Map.of(Group.VARIANT, variantValues(input, NO_MAPPING, genomicVariant.getChromosome(),
				genomicVariant.getPosition(), genomicVariant.getRefBase(), genomicVariant.getAltBase(), null, null)));
	}

	// A row for an invalid input
	String[] getCsvDataInvalidInput(VariantInput input, CsvLayout layout) {
		String notes = input.getMessages().stream().map(Message::toString).collect(Collectors.joining(";"));
		return layout.row(Map.of(Group.VARIANT, variantValues(input, notes, null, null, null, null, null, null)));
	}

	private String[] getCsvData(String notes, Gene gene,
								VariantInput input,
								GenomicVariant genomicVariant,
								AnnotationData annData,
								CsvLayout layout) {
		List<Isoform> isoforms = gene.getIsoforms();
		if (isoforms == null || isoforms.isEmpty()) {
			return getCsvDataMappingNotFound(input, genomicVariant, layout);
		}
		Isoform isoform = isoforms.get(0);
		String chr = genomicVariant.getChromosome();
		Integer genomicLocation = genomicVariant.getPosition();
		String varAllele = genomicVariant.getAltBase();

		Map<Group, List<String>> values = new EnumMap<>(Group.class);
		values.put(Group.VARIANT, variantValues(input, notes, chr, genomicLocation, gene.getRefAllele(), varAllele,
				knownIn(annData, chr, genomicLocation, varAllele),
				curated(annData, chr, genomicLocation, varAllele, isoform.getAccession())));
		values.put(Group.MAPPING, Arrays.asList(
				gene.getGeneName(),
				isoform.getCodonChange(),
				gene.isReverseStrand() ? "-" : "+",
				gene.getCaddScore() == null ? null : gene.getCaddScore().toString(),
				gene.getAviScore() == null ? null : gene.getAviScore().toString(),
				gene.getPangolin() == null ? null : String.valueOf(gene.getPangolin().score()),
				isoform.isCanonical() ? isoform.getAccession() : "(" + isoform.getAccession() + ")",
				buildAlternateInformDetails(isoforms),
				addTranscripts(isoform.getTranscripts()).toString(),
				isoform.getProteinName(),
				String.valueOf(isoform.getIsoformPosition()),
				isoform.getAminoAcidChange(),
				isoform.getConsequences(),
				popEve(isoform.getPopEveScore()),
				alphaMissense(isoform.getAmScore())));

		if (layout.has(Group.FUNCTION))
			values.put(Group.FUNCTION, csvFunctionDataBuilder.build(isoform, annData));
		if (layout.has(Group.PREDICTION))
			values.put(Group.PREDICTION, csvFunctionDataBuilder.buildPredictions(isoform, annData));
		if (layout.has(Group.POPULATION))
			values.put(Group.POPULATION, csvPopulationDataBuilder.build(isoform, chr, genomicLocation, varAllele, annData));
		// protein structures would have been preloaded in the cache when str=true.
		// Skip the lookup entirely otherwise — preloadStructureCache isn't called,
		// so each per-row getStr would be a cold cache miss against rel_*_structure.
		if (layout.has(Group.STRUCTURE)) {
			List<StructureResidue> proteinStructure = structureService.getStr(isoform.getAccession(),
					isoform.getIsoformPosition());
			if (proteinStructure != null)
				values.put(Group.STRUCTURE, List.of(csvStructureDataBuilder.build(proteinStructure)));
		}
		return layout.row(values);
	}

	// e.g. "dbSNP;ClinVar"
	static String knownIn(AnnotationData annData, String chr, Integer pos, String alt) {
		if (annData.getKnownCurated() == null || alt == null) return null;
		Integer bits = annData.getKnownCurated().known().get(VariantKey.genomic(chr, pos, alt.toUpperCase()));
		if (bits == null || bits == 0) return null;
		return Arrays.stream(KnownSource.values()).filter(s -> (bits & s.getBit()) != 0)
				.map(KnownSource::getLabel).collect(Collectors.joining(";"));
	}

	// e.g. "LP/P;US"
	static String curated(AnnotationData annData, String chr, Integer pos, String alt, String accession) {
		if (annData.getKnownCurated() == null || alt == null) return null;
		Integer bits = annData.getKnownCurated().curated()
				.get(VariantKey.genomic(chr, pos, alt.toUpperCase()) + ":" + accession);
		if (bits == null || bits == 0) return null;
		return Arrays.stream(CuratedCategory.values()).filter(c -> c != CuratedCategory.ANY && (bits & c.getBit()) != 0)
				.map(CuratedCategory::getLabel).collect(Collectors.joining(";"));
	}

	// e.g. "0.42(likely benign)"
	static String alphaMissense(AmScore am) {
		if (am == null) return null;
		return am.getAmPathogenicity() + "(" + am.getAmClass() + ")";
	}

	static String popEve(PopEveScore popEve) {
		if (popEve == null || popEve.getPopeve() == null) return null;
		PopEveClass popEveClass = PopEveClass.fromScore(popEve.getPopeve());
		return popEve.getPopeve() + (popEveClass == null ? "" : "(" + popEveClass.getLabel() + ")");
	}

	private String buildAlternateInformDetails(List<Isoform> value) {
		List<String> isformDetails = new ArrayList<>();
		for (Isoform mapping: value) {
			if (mapping.isCanonical())
				continue;
			List<String> transcripts = addTranscripts(mapping.getTranscripts());
			isformDetails.add(
				mapping.getAccession() + ";" +
					mapping.getIsoformPosition() + ";" +
					mapping.getAminoAcidChange() + ";" +
					mapping.getConsequences() + ";" + transcripts);
		}
		return String.join("|", isformDetails);
	}

	private List<String> addTranscripts(List<Transcript> transcripts) {
		return transcripts.stream().map(transcript -> transcript.getEnsp() + "(" + transcript.getEnst() + ")").collect(Collectors.toList());
	}

	public static String formatDuration(long millis) {
		Duration d = Duration.ofMillis(millis);
		return String.format("%02d:%02d:%02d",
				d.toHours(),
				d.toMinutes() % 60,
				d.getSeconds() % 60);
	}
}
