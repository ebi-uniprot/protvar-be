package uk.ac.ebi.protvar.fetcher.csv;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
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

import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputCache;
import uk.ac.ebi.protvar.fetcher.CustomInputMapping;
import uk.ac.ebi.protvar.fetcher.ProteinInputMapping;
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

@Service
@RequiredArgsConstructor
public class CSVProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(CSVProcessor.class);

	private static final String NO_MAPPING = "No mapping found";

	private static final String CSV_HEADER_INPUT = "User_input,Chromosome,Coordinate,ID,Reference_allele,Alternative_allele";
	private static final String CSV_HEADER_NOTES = "Notes";
	private static final String CSV_HEADER_OUTPUT_MAPPING = "Gene,Codon_change,Strand,CADD_phred_like_score,"
		+ "Canonical_isoform_transcripts,MANE_transcript,Uniprot_canonical_isoform_(non_canonical),"
		+ "Alternative_isoform_mappings,Protein_name,Amino_acid_position,Amino_acid_change,Consequences"; //len=12
	private static final String CSV_HEADER_OUTPUT_FUNCTION = "Residue_function_(evidence),Region_function_(evidence),"
		+ "Protein_existence_evidence,Protein_length,Entry_last_updated,Sequence_last_updated,Protein_catalytic_activity,"
		+ "Protein_complex,Protein_sub_cellular_location,Protein_family,Protein_interactions_PROTEIN(gene),"
		+ "Predicted_pockets(energy;per_vol;score;resids),Predicted_interactions(chainA-chainB;a_resids;b_resids;pDockQ),"
		+ "Foldx_prediction(foldxDdg;plddt),Conservation_score,AlphaMissense_pathogenicity(class),EVE_score(class),ESM1b_score"; // len=18
	private static final String CSV_HEADER_OUTPUT_POPULATION = "Genomic_location,Cytogenetic_band,Other_identifiers_for_the_variant,"
		+ "Diseases_associated_with_variant,Variants_colocated_at_residue_position"; // len=5
	private static final String CSV_HEADER_OUTPUT_STRUCTURE = "Position_in_structures"; // len=1
	// total len=36
	private static final String CSV_HEADER_OUTPUT = CSV_HEADER_OUTPUT_MAPPING + Constants.COMMA + CSV_HEADER_OUTPUT_FUNCTION + Constants.COMMA
		+ CSV_HEADER_OUTPUT_POPULATION + Constants.COMMA + CSV_HEADER_OUTPUT_STRUCTURE;

	static final String CSV_HEADER = CSV_HEADER_INPUT + Constants.COMMA + CSV_HEADER_NOTES + Constants.COMMA + CSV_HEADER_OUTPUT;

	private final AsyncTaskExecutor partitionTaskExecutor;
	private final CustomInputMapping customInputMapping;
	private final ProteinInputMapping proteinInputMapping;

	private final CSVFunctionDataFetcher functionDataFetcher;
	private final CSVPopulationDataFetcher populationFetcher;
	private final CSVStructureDataFetcher csvStructureDataFetcher;

	private final MappingRepo mappingRepo;
	private final InputCache inputCache;

	private final BuildProcessor buildProcessor;

	@Value("${app.data.folder}")
	private String dataFolder;

	@Value("${app.tmp.folder}")
	private String tmpFolder;

	@Value("${csv.partition.size:1000}")
	private int partitionSize;

	@Transactional(readOnly = true) // Avoid unnecessary locks in the DB.
	public void process(DownloadRequest request) {
		LOGGER.info("Started processing download request: {}", request);
		List<String> inputs = null;
		String inputId = null;
		InputBuild build = null;
		try {
			Path zipFilePath = Path.of(dataFolder, request.getFname() + ".csv.zip");
			if (Files.exists(zipFilePath)) {
				LOGGER.warn("{} exists", zipFilePath);
				return;
			}

			switch (request.getType()) {
				case ID:
					inputId = request.getInput();
					String originalInput = inputCache.getInput(inputId);
					if (originalInput == null) {
						LOGGER.warn("{} id not found", inputId);
						return;
					} else {
						List<String> originalInputList = Arrays.asList(originalInput.split("\\R|,"));
						build = buildProcessor.determinedBuild(inputId, originalInputList, request.getAssembly());
						if (request.getPage() == null) {
							inputs = originalInputList;
						} else {
							Integer pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
							inputs = PagedMappingService.getPage(originalInputList, request.getPage(), pageSize);
						}
					}
					break;

				case PROTEIN_ACCESSION:
					// assembly irrelevant for protein accession input
					String proteinAcc = request.getInput();
					if (request.getPage() == null) {
						LOGGER.info("Fetching all genomic inputs for protein accession {}", proteinAcc);
						inputs = mappingRepo.getGenInputsByAccession(proteinAcc, null, null);
					} else {
						Integer pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
						inputs = mappingRepo.getGenInputsByAccession(proteinAcc, request.getPage(), pageSize);
					}
					break;

				case SINGLE_VARIANT:
					String singleVariant = request.getInput();
					inputs = Arrays.asList(singleVariant);
					break;

			}

			if (inputs == null || inputs.isEmpty()) {
				LOGGER.warn("no inputs to generate download file");
				return;
			}

			// Process CSV
			if (inputs.size() <= partitionSize) {
				// Small job: process in main thread
				LOGGER.info("Processing input: {} (fname: {}, size: {})", request.getInput(), request.getFname(), inputs.size());

				Path csvFilePath = Path.of(tmpFolder, request.getFname() + ".csv");
				// Stream the input processing and writing to CSV directly
				try (Stream<String[]> rowsStream = processInputs(build, inputs, request)) {
					writeCsv(csvFilePath, rowsStream, true);  // true -> write header
				}

				FileUtils.zipFile(csvFilePath, zipFilePath);
				Files.deleteIfExists(csvFilePath);
			} else {
				// Large job: partition and process in parallel
				List<List<String>> partitions = Lists.partition(inputs, partitionSize);
				List<Future<Path>> futures = new ArrayList<>();
				final InputBuild inputBuild = build;

				// Submitting tasks to shared TaskExecutor for partitioned data
				for (int i = 0; i < partitions.size(); i++) {
					final int partitionNumber = i+1;
					List<String> partition = partitions.get(i);
					futures.add(partitionTaskExecutor.submit(() -> {
						LOGGER.info("Processing input partition #{}: {} (fname: {}, size: {})", partitionNumber, request.getInput(), request.getFname(), partition.size());
						// Stream the input processing and writing to CSV without header
						try (Stream<String[]> rowsStream = processInputs(inputBuild, partition, request)) {
							Path csvFile = Path.of(tmpFolder, request.getFname() + "_" + partitionNumber + ".csv");
							writeCsv(csvFile, rowsStream, false);  // false -> do not write header in partitions
							return csvFile;
						}
					}));
				}
				// Wait for all CSVs to be written
				List<Path> csvFiles = new ArrayList<>();
				for (Future<Path> future : futures) {
					csvFiles.add(future.get()); // blocking wait
				}
				// Merge all CSVs
				Path mergedCsv = Path.of(tmpFolder, request.getFname() + ".csv");
				mergeCsvFiles(csvFiles, mergedCsv);

				// Zip merged CSV
				FileUtils.zipFile(mergedCsv, zipFilePath);

				// Cleanup
				for (Path file : csvFiles) {
					Files.deleteIfExists(file);
				}
				Files.deleteIfExists(mergedCsv);
			}

			// Notify User
			Email.notifyUser(request);
		} catch (Exception e) {
			if (e instanceof CannotGetJdbcConnectionException) { // skip printing stack trace
				LOGGER.error("Job failed for {}: CannotGetJdbcConnectionException", request.getFname());
			} else { // other exceptions
				LOGGER.error("Job failed for {}: {}", request.getFname(), e.getMessage(), e);
			}
			Email.notifyUserErr(request, inputs);
			Email.notifyDevErr(request, inputs, e);
		}
		LOGGER.info("Finished processing download request: {}", request.getFname());
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
		return buildCSVResult(params, request);
	}

	private void writeCsv(Path file, Stream<String[]> rowsStream, boolean writeHeader) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(file))) {
			// Write the header first if necessary
			if (writeHeader) {
				writer.writeNext(CSV_HEADER.split(","));
			}
			// Write the data rows using stream
			rowsStream.forEach(writer::writeNext);
		}
	}

	private void mergeCsvFiles(List<Path> csvFiles, Path mergedFile) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(mergedFile))) {
			// Write the header first
			writer.writeNext(CSV_HEADER.split(","));

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
	private Stream<String[]> buildCSVResult(InputParams params, DownloadRequest request) {
		MappingResponse response = request.getType() == InputType.PROTEIN_ACCESSION ?
				proteinInputMapping.getMappings(request.getInput(), params) :
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
					.forEach(gene -> builder.add(getCSVData(notes, gene, chr, genomicLocation, varAllele, id, input, params)));
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
		valList.addAll(Collections.nCopies(CSV_HEADER_OUTPUT.split(Constants.COMMA).length, Constants.NA));
		return valList.toArray(String[]::new); // Return a String[] array
	}

	// Method to generate CSV data for invalid input
	String[] getCsvDataInvalidInput(UserInput input){
		List<String> valList = new ArrayList<>();
		valList.add(input.getInputStr()); // User_input
		valList.addAll(Collections.nCopies(5, Constants.NA)); // Chromosome,Coordinate,ID,Reference_allele,Alternative_allele
		valList.add(input.getMessages().stream().map(Message::toString).collect(Collectors.joining(";"))); // Notes
		valList.addAll(Collections.nCopies(CSV_HEADER_OUTPUT.split(Constants.COMMA).length, Constants.NA));
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

	private String[] getCSVData(String notes, Gene gene, String chr, Integer genomicLocation, String varAllele, String id, String input,
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
				transcripts.toString(), Constants.NA,mapping.getAccession(), CSVUtils.getValOrNA(alternateInformDetails), mapping.getProteinName(),
			String.valueOf(mapping.getIsoformPosition()), mapping.getAminoAcidChange(),
			mapping.getConsequences()));

		if (params.isFun() && mapping.getReferenceFunction() != null)
			output.addAll(functionDataFetcher.fetch(mapping));
		else
			addNaForNonRequestedData(output, CSV_HEADER_OUTPUT_FUNCTION);

		if (params.isPop() && mapping.getPopulationObservations() != null)
				output.addAll(populationFetcher.fetch(mapping.getPopulationObservations(), mapping.getRefAA(), mapping.getVariantAA(), genomicLocation));
		else
			addNaForNonRequestedData(output, CSV_HEADER_OUTPUT_POPULATION);

		if (params.isStr() && mapping.getProteinStructure() != null)
			output.add(csvStructureDataFetcher.fetch(mapping.getProteinStructure()));
		else
			addNaForNonRequestedData(output, CSV_HEADER_OUTPUT_STRUCTURE);

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
