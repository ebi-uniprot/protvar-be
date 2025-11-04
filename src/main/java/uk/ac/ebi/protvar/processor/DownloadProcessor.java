package uk.ac.ebi.protvar.processor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.fetcher.*;
import uk.ac.ebi.protvar.fetcher.csv.CsvFunctionDataBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CsvPopulationDataBuilder;
import uk.ac.ebi.protvar.fetcher.csv.CsvStructureDataBuilder;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.mapper.AnnotationData;
import uk.ac.ebi.protvar.mapper.AnnotationFetcher;
import uk.ac.ebi.protvar.mapper.MappingData;
import uk.ac.ebi.protvar.mapper.InputMapper;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.model.SearchTerm;
import uk.ac.ebi.protvar.types.SearchType;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.service.InputCacheService;
import uk.ac.ebi.protvar.service.InputService;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.utils.*;

/**
 * Handles CSV download requests received from RabbitMQ.
 *
 * - Requests are processed asynchronously using `downloadJobExecutor` (5 parallel jobs max).
 * - Large jobs are split into ~1000-input partitions, processed via `partitionProcessingExecutor`.
 * - A semaphore (10 permits) limits concurrent DB-heavy partition tasks to avoid overload.
 * - Each partition streams results to CSV, merged and zipped after all parts complete.
 *
 * Limits:
 * - Max concurrent jobs: 5 (RabbitMQ concurrency & job executor)
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
	private final AsyncTaskExecutor partitionProcessingExecutor;
	private final CsvFunctionDataBuilder csvFunctionDataBuilder;
	private final CsvPopulationDataBuilder csvPopulationDataBuilder;
	private final CsvStructureDataBuilder csvStructureDataBuilder;
	private final InputService inputService;
	private final InputCacheService inputCacheService;
	private final CachedInputHandler cachedInputHandler;
	private final SearchInputHandler searchInputHandler;
	private final InputMapper inputMapper;
	private final AnnotationFetcher annotationFetcher;
	private final StructureService structureService;
	@Value("${app.data.folder}")
	private String dataFolder;
	@Value("${app.tmp.folder}")
	private String tmpFolder;

    // Semaphore limiting DB-heavy task concurrency
    private final Semaphore dbTaskSemaphore = new Semaphore(10);

	// Note:
	// moved @Transactional from process() to a deeper method that actually hits the DB.
	// Each task (partition) still gets its own DB connection (via Hikari).
	// So 20 parallel partitions == 20 connections.
	public void process(DownloadRequest request) {
		LOGGER.info("[{}] Download request started", request.getFname());
		long start = System.currentTimeMillis();
		try {
			Path zipPath = Path.of(dataFolder, request.getFname() + ".csv.zip");
			if (Files.exists(zipPath)) {
				LOGGER.warn("Download file already exists: {}", zipPath);
				return; // Skip processing if file already exists
			}

			handleDownload(request, zipPath);

			// Notify User
			Email.notifyUser(request);
		} catch (Exception e) {
			handleException(e, request, List.of()/*inputs*/); // pass input first N lines
		}
		long end = System.currentTimeMillis();
		long durationMs = end - start;

		LOGGER.info("[{}] Download request completed in {}", request.getFname(), formatDuration(durationMs));
	}

	private void handleDownload(DownloadRequest request, Path zipPath) throws Exception {
		Path csvPath = Path.of(tmpFolder, request.getFname() + ".csv");
		boolean fun = Boolean.TRUE.equals(request.getFunction());
		boolean pop = Boolean.TRUE.equals(request.getPopulation());
		boolean str = Boolean.TRUE.equals(request.getStructure());

        // Determine the download type based on search terms
        if (request.isSingleVariant()) {
            // Single variant download
            handleSingleVariantDownload(request, csvPath, fun, pop, str);
        } else if (request.isInputIdQuery()) {
            // Input ID download (cached variants)
            handleInputIdDownload(request, csvPath, fun, pop, str);
        } else {
            // Identifier-based or filter-only download
            handleIdentifierDownload(request, csvPath, fun, pop, str);
        }

		// Zip final CSV
		FileUtils.zipFile(csvPath, zipPath);
		Files.deleteIfExists(csvPath);
	}

    /**
     * Handle single variant download.
     */
    private void handleSingleVariantDownload(DownloadRequest request, Path csvPath,
                                             boolean fun, boolean pop, boolean str) throws Exception {
        LOGGER.info("Single variant download request: {}", request.getFname());

        String variantStr = request.getSearchTerms().get(0).getValue();
        List<VariantInput> inputs = VariantParser.parse(List.of(variantStr));

        processAndWriteCsv(inputs, csvPath, request.getAssembly(), null, fun, pop, str, true);
    }

    /**
     * Handle input ID download (from cache).
     */
    private void handleInputIdDownload(DownloadRequest request, Path csvPath,
                                       boolean fun, boolean pop, boolean str) throws Exception {
        LOGGER.info("Input ID download request: {}", request.getFname());

        String inputId = request.getSearchTerms().get(0).getValue();

        // Ensure that the build is detected and cached
        inputService.detectBuild(InputRequest.builder()
                .inputId(inputId)
                .assembly(request.getAssembly())
                .build());

        InputBuild build = inputCacheService.getBuild(inputId);

        if (!Boolean.TRUE.equals(request.getFull())) {
            // Paged download: process in main thread
            LOGGER.info("Paged input ID download: {}", request.getFname());
            List<VariantInput> inputs = cachedInputHandler.pagedInput(request).getContent();
            processAndWriteCsv(inputs, csvPath, request.getAssembly(), build, fun, pop, str, true);
        } else {
            // Full download: partition and process in parallel if large
            LOGGER.info("Full input ID download: {}", request.getFname());
            processFullDownload(cachedInputHandler, request, csvPath, build, fun, pop, str);
        }
    }

    /**
     * Handle identifier-based or filter-only download.
     */
    private void handleIdentifierDownload(DownloadRequest request, Path csvPath,
                                          boolean fun, boolean pop, boolean str) throws Exception {
        List<SearchTerm> identifiers = request.getIdentifierTerms();

        if (identifiers.isEmpty()) {
            LOGGER.info("Filter-only download request: {}", request.getFname());
        } else if (identifiers.size() == 1) {
            LOGGER.info("Single identifier download request: {} = {}",
                    identifiers.get(0).getType(), identifiers.get(0).getValue());

            // Preload optimization for single UniProt accession full downloads
            if (Boolean.TRUE.equals(request.getFull()) &&
                    identifiers.get(0).getType() == SearchType.UNIPROT) {
                String accession = identifiers.get(0).getValue();
                // TODO: preload full accession data if needed
                LOGGER.debug("Preloading data for UniProt accession: {}", accession);
            }
        } else {
            LOGGER.info("Multiple identifiers download request: {} identifiers", identifiers.size());
        }

        if (!Boolean.TRUE.equals(request.getFull())) {
            // Paged download: process in main thread
            LOGGER.info("Paged identifier download: {}", request.getFname());
            List<VariantInput> inputs = searchInputHandler.pagedInput(request)
                    .getContent();
            processAndWriteCsv(inputs, csvPath, request.getAssembly(), null, fun, pop, str, true);
        } else {
            // Full download: partition and process in parallel if large
            LOGGER.info("Full identifier download: {}", request.getFname());
            processFullDownload(searchInputHandler, request, csvPath, null, fun, pop, str);
        }
    }

	private void processAndWriteCsv(List<VariantInput> inputs, Path outputPath, String assembly, InputBuild build,
                                    boolean fun, boolean pop, boolean str, boolean writeHeader) throws Exception {
		inputMapper.preprocess(inputs, assembly, build);

		MappingData coreMapping = inputMapper.loadCoreMappingAndScores(inputs);
		if (coreMapping != null) {
			AnnotationData annData = annotationFetcher.preloadOptionalAnnotations(coreMapping, fun, pop, str);

			try (Stream<String[]> rows = streamInputsToCsv(inputs, coreMapping, annData)) {
				writeCsv(outputPath, rows, writeHeader);
			}
		}
	}

    /**
     * Process in parallel, partitioning the input into chunks.
     */
	private void processFullDownload(InputHandler inputHandler, DownloadRequest request, Path csvPath,
									 InputBuild build, boolean fun, boolean pop, boolean str) throws Exception {
		AtomicInteger chunkIndex = new AtomicInteger(0);
		List<Future<Path>> futures = new ArrayList<>();

		try (Stream<List<VariantInput>> chunkStream = inputHandler.streamChunkedInput(request)) {
			for (List<VariantInput> chunk : (Iterable<List<VariantInput>>) chunkStream::iterator) {
				int chunkNum = chunkIndex.getAndIncrement();
				// Limit concurrent DB/file-processing
				dbTaskSemaphore.acquire(); // blocks if limit reached

				futures.add(partitionProcessingExecutor.submit(() -> {
					try {
						LOGGER.info("[{}] Processing chunk #{}", request.getFname(), chunkNum);
						Path partPath = Path.of(tmpFolder, request.getFname() + "_" + chunkNum + ".csv");
						processAndWriteCsv(chunk, partPath, request.getAssembly(), build, fun, pop, str, false);
						return partPath;
					} finally {
						dbTaskSemaphore.release();
					}
				}));
			}
		}

		// Wait for all CSV parts to be written
		List<Path> csvParts = new ArrayList<>();
		for (Future<Path> future : futures) {
			csvParts.add(future.get()); // blocking wait
		}

		// Merge all parts
		mergeCsvFiles(csvParts, csvPath);

        // Clean up temporary files (uncomment if needed)
		// for (Path part : csvParts) Files.deleteIfExists(part);
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

	private void writeCsv(Path file, Stream<String[]> rowsStream, boolean writeHeader) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(file))) {
			// Write the header first if necessary
			if (writeHeader) {
				writer.writeNext(CsvHeaders.CSV_HEADER.split(","));
			}
			// Write the data rows using stream
			rowsStream.forEach(writer::writeNext);
		}
	}

	private void mergeCsvFiles(List<Path> csvFiles, Path mergedFile) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(mergedFile))) {
			// Write the header first
			writer.writeNext(CsvHeaders.CSV_HEADER.split(","));

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

    /**
     * Stream the inputs and expand them based on input types.
     */
	private Stream<String[]> streamInputsToCsv(List<VariantInput> inputs,
											   MappingData coreMapping,
												AnnotationData annData) {
		// Stream the inputs instead of collecting them into a list
		Stream.Builder<String[]> builder = Stream.builder();
		inputs.stream()
			.filter(VariantInput::isValid)
			.forEach(input -> {
				if (!input.isValid()) {
					// Invalid input, add one row
					builder.add(getCsvDataInvalidInput(input));
					return;
				}
				// Process the input and generate CSV rows
				inputMapper.processInput(input, coreMapping);
				input.getDerivedGenomicVariants()
						.forEach(genomicVariant -> generateGenomicCsvRows(builder, input, genomicVariant, annData));
		});

		return builder.build();
	}

	private void generateGenomicCsvRows(Stream.Builder<String[]> builder,
										VariantInput input,
										GenomicVariant genomicVariant,
										AnnotationData annData) {
		String messages = input.getMessages().stream().map(Message::toString)
				.filter(msg -> !msg.isEmpty()) // Filter for non-empty messages
				.collect(Collectors.joining(";"));
		final String notes = messages.isEmpty() ? Constants.NA : messages;

		var genes = genomicVariant.getGenes();
		if (genes.isEmpty())
			builder.add(getCsvDataMappingNotFound(input, genomicVariant));
		else
			genes.stream()
					.forEach(gene -> builder.add(getCsvData(notes, gene, input, genomicVariant, annData)));
	}

	String idValue(VariantInput input) {
		return input.getType() == VariantType.VARIANT_ID ? input.getInputStr() :
				(input.getFormat() == VariantFormat.VCF ? ((GenomicInput) input).getId() : null);
	}

    /**
     * Generate the CSV data for mapping not found.
     */
	String[] getCsvDataMappingNotFound(VariantInput input, GenomicVariant genomicVariant){
		String id = idValue(input);
		List<String> valList = new ArrayList<>();
		valList.add(input.getInputStr()); // User_input
		valList.add(strValOrNA(genomicVariant.getChromosome())); // Chromosome,Coordinate,ID,Reference_allele,Alternative_allele
		valList.add(intValOrNA(genomicVariant.getPosition()));
		valList.add(strValOrNA(id));
		valList.add(strValOrNA(genomicVariant.getRefBase()));
		valList.add(strValOrNA(genomicVariant.getAltBase()));
		valList.add(NO_MAPPING); // Notes
		valList.addAll(Collections.nCopies(CsvHeaders.OUTPUT_LENGTH, Constants.NA));
		return valList.toArray(String[]::new); // Return a String[] array
	}

    /**
     * Generate CSV data for invalid input.
     */
	String[] getCsvDataInvalidInput(VariantInput input){
		List<String> valList = new ArrayList<>();
		valList.add(input.getInputStr()); // User_input
		valList.addAll(Collections.nCopies(5, Constants.NA)); // Chromosome,Coordinate,ID,Reference_allele,Alternative_allele
		valList.add(input.getMessages().stream().map(Message::toString).collect(Collectors.joining(";"))); // Notes
		valList.addAll(Collections.nCopies(CsvHeaders.OUTPUT_LENGTH, Constants.NA));
		return valList.toArray(String[]::new);
	}

	String strValOrNA(String val) {
		if (val == null || val.trim().isEmpty())
			return Constants.NA;
		return val.trim();
	}

	String intValOrNA(Integer val) {
		if (val == null)
			return Constants.NA;
		return val.toString();
	}

	private String[] getCsvData(String notes, Gene gene,
								VariantInput input,
								GenomicVariant genomicVariant,
								AnnotationData annData) {
		String chr = genomicVariant.getChromosome();
		Integer genomicLocation = genomicVariant.getPosition();
		String varAllele = genomicVariant.getAltBase();

		String id = idValue(input);

		String cadd = null;
		if (gene.getCaddScore() != null)
			cadd = gene.getCaddScore().toString();

		String strand = gene.isReverseStrand() ? "-" : "+";
		Isoform isoform = gene.getIsoforms().get(0);

		var alternateInformDetails = buildAlternateInformDetails(gene.getIsoforms());
		List<String> transcripts = addTranscripts(isoform.getTranscripts());

        List<String> output = new ArrayList<>(Arrays.asList(
                input.getInputStr(), chr, genomicLocation.toString(), id, gene.getRefAllele(),
                varAllele, notes, gene.getGeneName(), isoform.getCodonChange(), strand, cadd,
                transcripts.toString(), Constants.NA, isoform.getAccession(),
                CsvUtils.getValOrNA(alternateInformDetails), isoform.getProteinName(),
                String.valueOf(isoform.getIsoformPosition()), isoform.getAminoAcidChange(),
                isoform.getConsequences()
        ));

		List<String> funData = csvFunctionDataBuilder.build(isoform, annData);
		if (funData != null && !funData.isEmpty()) {
			output.addAll(funData);
		} else {
			addNaForNonRequestedData(output, CsvHeaders.OUTPUT_FUNCTION);
		}

		List<String> popData = csvPopulationDataBuilder.build(isoform, chr, genomicLocation, varAllele, annData);
		if (popData != null && !popData.isEmpty()) {
			output.addAll(popData);
		} else {
			addNaForNonRequestedData(output, CsvHeaders.OUTPUT_POPULATION);
		}

        // Protein structures would have been preloaded in the cache
        List<StructureResidue> proteinStructure = structureService.getStr(
                isoform.getAccession(),
                isoform.getIsoformPosition()
        );

		if (annData.isStr() && proteinStructure != null)
			output.add(csvStructureDataBuilder.build(proteinStructure));
		else
			addNaForNonRequestedData(output, CsvHeaders.OUTPUT_STRUCTURE);

		return output.toArray(String[]::new);
	}

	private void addNaForNonRequestedData(List<String> output, String header) {
		for (int i = 0; i < header.split(Constants.COMMA).length; i++)
			output.add(Constants.NA);
	}

	private String buildAlternateInformDetails(List<Isoform> value) {
		List<String> isoformDetails = new ArrayList<>();
		for (Isoform mapping: value) {
			if (mapping.isCanonical())
				continue;
			List<String> transcripts = addTranscripts(mapping.getTranscripts());
			isoformDetails.add(
				mapping.getAccession() + ";" +
					mapping.getIsoformPosition() + ";" +
					mapping.getAminoAcidChange() + ";" +
					mapping.getConsequences() + ";" + transcripts);
		}
		return String.join("|", isoformDetails);
	}

	private List<String> addTranscripts(List<Transcript> transcripts) {
        return transcripts.stream()
                .map(transcript -> transcript.getEnsp() + "(" + transcript.getEnst() + ")")
                .collect(Collectors.toList());
	}

	public static String formatDuration(long millis) {
		Duration d = Duration.ofMillis(millis);
		return String.format("%02d:%02d:%02d",
				d.toHours(),
				d.toMinutes() % 60,
				d.getSeconds() % 60);
	}
}
