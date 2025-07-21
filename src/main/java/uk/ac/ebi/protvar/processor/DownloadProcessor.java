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
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.mapper.AnnotationData;
import uk.ac.ebi.protvar.mapper.AnnotationFetcher;
import uk.ac.ebi.protvar.mapper.MappingData;
import uk.ac.ebi.protvar.mapper.UserInputMapper;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.UserInputRequest;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.service.UserInputCacheService;
import uk.ac.ebi.protvar.service.UserInputService;
import uk.ac.ebi.protvar.types.InputType;
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
	private final UserInputService userInputService;
	private final UserInputCacheService userInputCacheService;
	private final UserInputHandler userInputHandler;
	private final SearchInputHandler searchInputHandler;
	private final UserInputMapper userInputMapper;
	private final AnnotationFetcher annotationFetcher;
	private final StructureService structureService;
	@Value("${app.data.folder}")
	private String dataFolder;
	@Value("${app.tmp.folder}")
	private String tmpFolder;

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

			InputHandler handler = null;

			switch (request.getType()) {

				case SINGLE_VARIANT -> {
					LOGGER.info("Single variant download request: {}", request.getFname());
					// no handler needed for single input
				}
				case INPUT_ID -> {
					handler = userInputHandler;
					// ensure that the build is detected and cached
					userInputService.detectBuild(UserInputRequest.builder()
							.inputId(request.getInput())
							.assembly(request.getAssembly())
							.build());
				}
				case UNIPROT /*, ENSEMBL, PDB, REFSEQ, GENE*/ -> {
					handler = searchInputHandler;

					// preload stuff here!!
					if (request.getFull() != null && Boolean.TRUE.equals(request.getFull())
							&& request.getType() == InputType.UNIPROT) {
						String accession = request.getInput();

						// preload full accession data
					}


				}
				default -> throw new IllegalArgumentException("Unsupported type: " + request.getType());

			}

			handleDownload(handler, request, zipPath);
			// Notify User
			Email.notifyUser(request);
		} catch (Exception e) {
			handleException(e, request, List.of()/*inputs*/); // pass input first N lines
		}
		long end = System.currentTimeMillis();
		long durationMs = end - start;

		LOGGER.info("[{}] Download request completed in {}", request.getFname(), formatDuration(durationMs));
	}

	private void handleDownload(InputHandler inputHandler, DownloadRequest request, Path zipPath) throws Exception {
		Path csvPath = Path.of(tmpFolder, request.getFname() + ".csv");
		boolean fun = Boolean.TRUE.equals(request.getFunction());
		boolean pop = Boolean.TRUE.equals(request.getPopulation());
		boolean str = Boolean.TRUE.equals(request.getStructure());

		List<UserInput> userInputs;

		if (request.getType() == InputType.SINGLE_VARIANT) {
			userInputs = List.of(InputParser.parse(request.getInput()));
			processAndWriteCsv(userInputs, csvPath, request.getAssembly(), null, fun, pop, str, true);
			return;
		}

		InputBuild build = request.getType() != null && request.getType() == InputType.INPUT_ID ?
				userInputCacheService.getBuild(request.getInput()) : null; // use cached build

		if (!Boolean.TRUE.equals(request.getFull())) { // paged download: process in main thread
			LOGGER.info("Page download request: {}", request.getFname());
			userInputs = inputHandler.pagedInput(request).getContent();
			processAndWriteCsv(userInputs, csvPath, request.getAssembly(), build, fun, pop, str, true);
		} else { // full download: partition and process in parallel, if large
			LOGGER.info("Full download request: {}", request.getFname());
			processFullDownload(inputHandler, request, csvPath, build, fun, pop, str);
		}

		// Zip final CSV
		FileUtils.zipFile(csvPath, zipPath);
		Files.deleteIfExists(csvPath);
	}

	private void processAndWriteCsv(List<UserInput> inputs, Path outputPath, String assembly, InputBuild build,
									boolean fun, boolean pop, boolean str, boolean writeHeader) throws Exception {
		userInputMapper.preprocess(inputs, assembly, build);

		MappingData coreMapping = userInputMapper.loadCoreMappingAndScores(inputs);
		if (coreMapping != null) {
			AnnotationData annData = annotationFetcher.preloadOptionalAnnotations(inputs, coreMapping, fun, pop, str);

			try (Stream<String[]> rows = streamInputsToCsv(inputs, coreMapping, annData)) {
				writeCsv(outputPath, rows, writeHeader);
			}
		}
	}

	// Define a semaphore limiting DB-heavy task concurrency
	private final Semaphore dbTaskSemaphore = new Semaphore(10); // limit to 10 concurrent DB-hitting tasks

	// Process in parallel, partitioning the input into chunks
	private void processFullDownload(InputHandler inputHandler, DownloadRequest request, Path csvPath,
									 InputBuild build, boolean fun, boolean pop, boolean str) throws Exception {
		AtomicInteger chunkIndex = new AtomicInteger(0);
		List<Future<Path>> futures = new ArrayList<>();

		try (Stream<List<UserInput>> chunkStream = inputHandler.streamChunkedInput(request)) {
			for (List<UserInput> chunk : (Iterable<List<UserInput>>) chunkStream::iterator) {
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

		// uncomment to clean up
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

	// Stream the inputs and expand them based on input types
	private Stream<String[]> streamInputsToCsv(List<UserInput> inputs,
											   MappingData coreMapping,
												AnnotationData annData) {
		// Stream the inputs instead of collecting them into a list
		Stream.Builder<String[]> builder = Stream.builder();
		inputs.stream()
			.filter(UserInput::isValid)
			.forEach(input -> {
				if (!input.isValid()) {
					// Invalid input, add one row
					builder.add(getCsvDataInvalidInput(input));
					return;
				}
				// Process the input and generate CSV rows
				userInputMapper.processInput(input, coreMapping);
				input.getDerivedGenomicVariants()
						.forEach(genomicVariant -> generateGenomicCsvRows(builder, input, genomicVariant, annData));
		});

		return builder.build();
	}

	private void generateGenomicCsvRows(Stream.Builder<String[]> builder,
										UserInput userInput,
										GenomicVariant genomicVariant,
										AnnotationData annData) {
		String messages = userInput.getMessages().stream().map(Message::toString)
				.filter(msg -> !msg.isEmpty()) // Filter for non-empty messages
				.collect(Collectors.joining(";"));
		final String notes = messages.isEmpty() ? Constants.NA : messages;

		var genes = genomicVariant.getGenes();
		if (genes.isEmpty())
			builder.add(getCsvDataMappingNotFound(userInput, genomicVariant));
		else
			genes.stream()
					.forEach(gene -> builder.add(getCsvData(notes, gene, userInput, genomicVariant, annData)));
	}

	String idValue(UserInput userInput) {
		return userInput.getType() == Type.ID ? userInput.getInputStr() :
				(userInput.getFormat() == Format.VCF ? ((GenomicInput) userInput).getId() : null);
	}

	// Method to generate the CSV data for invalid mapping
	String[] getCsvDataMappingNotFound(UserInput userInput, GenomicVariant genomicVariant){
		String id = idValue(userInput);
		List<String> valList = new ArrayList<>();
		valList.add(userInput.getInputStr()); // User_input
		valList.add(strValOrNA(genomicVariant.getChr())); // Chromosome,Coordinate,ID,Reference_allele,Alternative_allele
		valList.add(intValOrNA(genomicVariant.getPos()));
		valList.add(strValOrNA(id));
		valList.add(strValOrNA(genomicVariant.getRef()));
		valList.add(strValOrNA(genomicVariant.getAlt()));
		valList.add(NO_MAPPING); // Notes
		valList.addAll(Collections.nCopies(CsvHeaders.OUTPUT_LENGTH, Constants.NA));
		return valList.toArray(String[]::new); // Return a String[] array
	}

	// Method to generate CSV data for invalid input
	String[] getCsvDataInvalidInput(UserInput input){
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
								UserInput userInput,
								GenomicVariant genomicVariant,
								AnnotationData annData) {
		String chr = genomicVariant.getChr();
		Integer genomicLocation = genomicVariant.getPos();
		String varAllele = genomicVariant.getAlt();

		String id = idValue(userInput);

		String cadd = null;
		if (gene.getCaddScore() != null)
			cadd = gene.getCaddScore().toString();
		String strand = "+";
		Isoform isoform = gene.getIsoforms().get(0);

		if (gene.isReverseStrand()) {
			strand = "-";
		}

		var alternateInformDetails = buildAlternateInformDetails(gene.getIsoforms());
		List<String> transcripts = addTranscripts(isoform.getTranscripts());

		List<String> output = new ArrayList<>(Arrays.asList(userInput.getInputStr(), chr, genomicLocation.toString(), id, gene.getRefAllele(),
			varAllele, notes, gene.getGeneName(), isoform.getCodonChange(), strand, cadd,
				transcripts.toString(), Constants.NA,isoform.getAccession(), CsvUtils.getValOrNA(alternateInformDetails), isoform.getProteinName(),
			String.valueOf(isoform.getIsoformPosition()), isoform.getAminoAcidChange(),
			isoform.getConsequences()));


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

		// protein structures would have been preloaded in the cache
		List<StructureResidue> proteinStructure = structureService.getStr(isoform.getAccession(),
				isoform.getIsoformPosition());

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
