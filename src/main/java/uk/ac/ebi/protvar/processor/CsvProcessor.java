package uk.ac.ebi.protvar.processor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputCache;
import uk.ac.ebi.protvar.fetcher.CustomInputMapping;
import uk.ac.ebi.protvar.fetcher.ProteinInputMapping;
import uk.ac.ebi.protvar.fetcher.csv.CsvFunctionDataFetcher;
import uk.ac.ebi.protvar.fetcher.csv.CsvPopulationDataFetcher;
import uk.ac.ebi.protvar.fetcher.csv.CsvStructureDataFetcher;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.input.processor.InputProcessor;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.IDInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.InputType;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.MappingRepo;
import uk.ac.ebi.protvar.service.PagedMappingService;
import uk.ac.ebi.protvar.utils.*;

import static uk.ac.ebi.protvar.constants.PagedMapping.DEFAULT_PAGE_SIZE;

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
public class CsvProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvProcessor.class);
	private static final String NO_MAPPING = "No mapping found";
	private final AsyncTaskExecutor partitionProcessingExecutor;
	private final CustomInputMapping customInputMapping;
	private final ProteinInputMapping proteinInputMapping;
	private final CsvFunctionDataFetcher functionDataFetcher;
	private final CsvPopulationDataFetcher populationFetcher;
	private final CsvStructureDataFetcher csvStructureDataFetcher;
	private final MappingRepo mappingRepo;
	private final InputCache inputCache;
	private final BuildProcessor buildProcessor;

	@Value("${app.data.folder}")
	private String dataFolder;

	@Value("${app.tmp.folder}")
	private String tmpFolder;

	@Value("${csv.partition.size:1000}")
	private int partitionSize;

	/**
	 *
	 * @param request
	 */
	// Note:
	// moved @Transactional from process() to a deeper method that actually hits the DB.
	// Each task (partition) still gets its own DB connection (via Hikari).
	// So 20 parallel partitions == 20 connections.
	public void process(DownloadRequest request) {
		LOGGER.info("Started processing download request: {}", request);
		List<String> inputs = null;
		try {
			Path zipPath = Path.of(dataFolder, request.getFname() + ".csv.zip");
			if (Files.exists(zipPath)) {
				LOGGER.warn("Download file already exists: {}", zipPath);
				return;
			}

			// Retrieve inputs and build object if needed
			Pair<InputBuild, List<String>> buildAndInputs = getInputsForRequest(request);
			if (buildAndInputs == null || buildAndInputs.getRight().isEmpty()) {
				LOGGER.warn("No inputs found for request: {}", request.getFname());
				return;
			}
			InputBuild build = buildAndInputs.getLeft();
			inputs = buildAndInputs.getRight();

			// Process request
			// Small job: process in main thread
			// Large job: partition and process in parallel
			if (inputs.size() <= partitionSize) {
				handleSmallJob(build, inputs, request, zipPath);
			} else {
				handleLargeJob(build, inputs, request, zipPath);
			}

			// Notify User
			Email.notifyUser(request);
		} catch (Exception e) {
			handleException(e, request, inputs);
		}
		LOGGER.info("Finished processing download request: {}", request.getFname());
	}

	private Pair<InputBuild, List<String>> getInputsForRequest(DownloadRequest request) {
		switch (request.getType()) {
			case ID -> {
				String inputId = request.getInput();
				String originalInput = inputCache.getInput(inputId);
				if (originalInput == null) {
					LOGGER.warn("Input ID not found: {}", inputId);
					return null;
				}
				List<String> inputList = Arrays.asList(originalInput.split("\\R|,"));
				InputBuild build = buildProcessor.determinedBuild(inputId, inputList, request.getAssembly());
				List<String> pagedInputs = request.getPage() == null
						? inputList
						: PagedMappingService.getPage(inputList, request.getPage(),
						Objects.requireNonNullElse(request.getPageSize(), DEFAULT_PAGE_SIZE));
				return Pair.of(build, pagedInputs);
			}
			case PROTEIN_ACCESSION -> {
				String acc = request.getInput();;
				List<String> accInputs = request.getPage() == null
						? mappingRepo.getGenInputsByAccession(acc, null, null)
						: mappingRepo.getGenInputsByAccession(acc, request.getPage(),
						Objects.requireNonNullElse(request.getPageSize(), DEFAULT_PAGE_SIZE));
				return Pair.of(null, accInputs);
			}
			case SINGLE_VARIANT -> {
				return Pair.of(null, List.of(request.getInput()));
			}
			default -> throw new IllegalArgumentException("Unsupported input type: " + request.getType());
		}
	}

	private void handleSmallJob(InputBuild build, List<String> inputs, DownloadRequest request, Path zipPath) throws IOException {
		LOGGER.info("Handling small job for request: {} ({} inputs)", request.getFname(), inputs.size());
		Path csvPath = Path.of(tmpFolder, request.getFname() + ".csv");
		// Stream the input processing and writing to CSV directly
		try (Stream<String[]> rowsStream = processInputs(build, inputs, request)) {
			writeCsv(csvPath, rowsStream, true);  // write header
		}

		FileUtils.zipFile(csvPath, zipPath);
		Files.deleteIfExists(csvPath);
	}

	// Define a semaphore limiting DB-heavy task concurrency
	private final Semaphore dbTaskSemaphore = new Semaphore(10); // limit to 10 concurrent DB-hitting tasks
	private void handleLargeJob(InputBuild build, List<String> inputs, DownloadRequest request, Path zipPath) throws Exception {
		LOGGER.info("Handling large job for request: {} ({} inputs)", request.getFname(), inputs.size());
		List<List<String>> partitions = Lists.partition(inputs, partitionSize);
		List<Future<Path>> futures = new ArrayList<>();

		// Submitting tasks to shared TaskExecutor for partitioned data
		for (int i = 0; i < partitions.size(); i++) {
			final int partitionNumber = i + 1;
			List<String> partition = partitions.get(i);
			futures.add(partitionProcessingExecutor.submit(() -> {
				try {
					dbTaskSemaphore.acquire(); // acquire before starting DB-heavy work
					LOGGER.info("Processing partition #{} for {} ({} inputs)", partitionNumber, request.getFname(), partition.size());
					Path partPath = Path.of(tmpFolder, request.getFname() + "_" + partitionNumber + ".csv");
					// Stream the input processing and writing to CSV without header
					try (Stream<String[]> rows = processInputs(build, partition, request)) {
						writeCsv(partPath, rows, false); // false -> do not write header in partitions
					}
					return partPath;
				} finally {
					dbTaskSemaphore.release(); // ensure release even on error
				}
			}));
		}

		// Wait for all CSV parts to be written
		List<Path> csvParts = new ArrayList<>();
		for (Future<Path> future : futures) {
			csvParts.add(future.get()); // blocking wait
		}
		// Merge all CSVs
		Path mergedCsv = Path.of(tmpFolder, request.getFname() + ".csv");
		mergeCsvFiles(csvParts, mergedCsv);
		// Zip merged CSV
		FileUtils.zipFile(mergedCsv, zipPath);
		// Cleanup
		for (Path part : csvParts) Files.deleteIfExists(part);
		Files.deleteIfExists(mergedCsv);
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

	private Stream<String[]> processInputs(InputBuild build,
										 List<String> inputs, DownloadRequest request) {
		InputParams params = InputParams.builder()
				.id(request.getInput()) // ID, PROTEIN_ACCESSION or SINGLE_VARIANT
				.inputs(InputProcessor.parse(inputs))
				.fun(request.isFunction())
				.pop(request.isPopulation())
				.str(request.isStructure())
				.assembly(request.getAssembly())
				.inputBuild(build) // Optional
				.build();
		// Use a stream to build CSV results directly instead of collecting all in memory
		return streamInputsToCsv(params, request);
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
	private Stream<String[]> streamInputsToCsv(InputParams params, DownloadRequest request) {
		MappingResponse response = request.getType() == InputType.PROTEIN_ACCESSION ?
				/* requires min 4 max 8 DB calls per call or acc (-3 for cached scores, pockets and interactions) */
				proteinInputMapping.getMappings(request.getInput(), params) :
				/* TODO count min/max DB calls */
				customInputMapping.getMapping(params);

		// Stream the inputs instead of collecting them into a list
		Stream.Builder<String[]> builder = Stream.builder();
		response.getInputs().stream().forEach(input -> {
			if (!input.isValid()) {
				// Invalid input, add one row
				builder.add(getCsvDataInvalidInput(input));
				return;
			}

			if (input.getType() == Type.GENOMIC) {
				GenomicInput userInput = (GenomicInput) input;
				generateGenomicCsvRows(builder, userInput, userInput, params);
			} else if (input.getType() == Type.CODING) {
				HGVSc userInput = (HGVSc) input;
				userInput.getDerivedGenomicInputs()
						.forEach(genInput -> generateGenomicCsvRows(builder, userInput, genInput, params));
			} else if (input.getType() == Type.PROTEIN) {
				ProteinInput userInput = (ProteinInput) input;
				userInput.getDerivedGenomicInputs()
						.forEach(genInput -> generateGenomicCsvRows(builder, userInput, genInput, params));
			} else if (input.getType() == Type.ID) {
				IDInput userInput = (IDInput) input;
				userInput.getDerivedGenomicInputs()
						.forEach(genInput -> generateGenomicCsvRows(builder, userInput, genInput, params));
			}
		});

		return builder.build();
	}

	private void generateGenomicCsvRows(Stream.Builder<String[]> builder,
													UserInput userInput,
													GenomicInput genInput,
													InputParams params) {
		String chr = genInput.getChr();
		Integer genomicLocation = genInput.getPos();
		String varAllele = genInput.getAlt();
		String id = genInput.getId();
		String input = genInput.getInputStr();
		String messages = Stream.concat(
						userInput.getMessages().stream().map(Message::toString),
						genInput.getMessages().stream().map(Message::toString)
				)
				.filter(msg -> !msg.isEmpty()) // Filter for non-empty messages
				.collect(Collectors.joining(";"));
		final String notes = messages.isEmpty() ? Constants.NA : messages;

		var genes = genInput.getGenes();
		if (genes.isEmpty())
			builder.add(getCsvDataMappingNotFound(genInput));
		else
			genes.stream()
					.forEach(gene -> builder.add(getCsvData(notes, gene, chr, genomicLocation, varAllele, id, input, params)));
	}

	// Method to generate the CSV data for invalid mapping
	String[] getCsvDataMappingNotFound(GenomicInput genInput){
		List<String> valList = new ArrayList<>();
		valList.add(genInput.getInputStr()); // User_input
		valList.add(strValOrNA(genInput.getChr())); // Chromosome,Coordinate,ID,Reference_allele,Alternative_allele
		valList.add(intValOrNA(genInput.getPos()));
		valList.add(strValOrNA(genInput.getId()));
		valList.add(strValOrNA(genInput.getRef()));
		valList.add(strValOrNA(genInput.getAlt()));
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

	private String[] getCsvData(String notes, Gene gene, String chr, Integer genomicLocation, String varAllele, String id, String input,
								InputParams params) {
		String cadd = null;
		if (gene.getCaddScore() != null)
			cadd = gene.getCaddScore().toString();
		String strand = "+";
		Isoform mapping = gene.getIsoforms().get(0);

		if (gene.isReverseStrand()) {
			strand = "-";
		}

		var alternateInformDetails = buildAlternateInformDetails(gene.getIsoforms());
		List<String> transcripts = addTranscripts(mapping.getTranscripts());

		List<String> output = new ArrayList<>(Arrays.asList(input, chr, genomicLocation.toString(), id, gene.getRefAllele(),
			varAllele, notes, gene.getGeneName(), mapping.getCodonChange(), strand, cadd,
				transcripts.toString(), Constants.NA,mapping.getAccession(), CsvUtils.getValOrNA(alternateInformDetails), mapping.getProteinName(),
			String.valueOf(mapping.getIsoformPosition()), mapping.getAminoAcidChange(),
			mapping.getConsequences()));

		if (params.isFun() && mapping.getReferenceFunction() != null)
			output.addAll(functionDataFetcher.fetch(mapping));
		else
			addNaForNonRequestedData(output, CsvHeaders.OUTPUT_FUNCTION);

		if (params.isPop() && mapping.getPopulationObservations() != null)
				output.addAll(populationFetcher.fetch(mapping.getPopulationObservations(), mapping.getRefAA(), mapping.getVariantAA(), genomicLocation));
		else
			addNaForNonRequestedData(output, CsvHeaders.OUTPUT_POPULATION);

		if (params.isStr() && mapping.getProteinStructure() != null)
			output.add(csvStructureDataFetcher.fetch(mapping.getProteinStructure()));
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
}
