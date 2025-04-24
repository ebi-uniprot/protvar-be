package uk.ac.ebi.protvar.fetcher.csv;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@AllArgsConstructor
public class CSVDataFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(CSVDataFetcher.class);

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

	private static final int PARTITION_SIZE = 1000;
	private CustomInputMapping customInputMapping;
	private ProteinInputMapping proteinInputMapping;

	private CSVFunctionDataFetcher functionDataFetcher;
	private CSVPopulationDataFetcher populationFetcher;
	private CSVStructureDataFetcher csvStructureDataFetcher;

	private MappingRepo mappingRepo;

	private String downloadDir;
	private InputCache inputCache;

	private BuildProcessor buildProcessor;

	@Transactional(readOnly = true) // Avoid unnecessary locks in the DB.
	public void writeCSVResult(DownloadRequest request) {
		LOGGER.info("Started processing download request: {}", request);
		List<String> inputs = null;
		String inputId = null;
		InputBuild inputBuild = null;
		try {
			Path zipPath = Paths.get(downloadDir, request.getFname() + ".csv.zip");
			if (Files.exists(zipPath)) {
				LOGGER.warn("{} exists", zipPath.getFileName());
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
						inputBuild = buildProcessor.determinedBuild(inputId, originalInputList, request.getAssembly());
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

			if (inputs == null || inputs.size() == 0) {
				LOGGER.warn("no inputs to generate download file");
				return;
			}

			// Write CSV
			Path csvPath = Paths.get(downloadDir, request.getFname() + ".csv");
			try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(csvPath));
				 CSVWriter writer = new CSVWriter(outputStreamWriter)) {
				writer.writeNext(CSV_HEADER.split(","));

				try {

					if (inputs.size() <= PARTITION_SIZE) {
						InputParams params = InputParams.builder()
								.id(inputId)
								.inputs(InputProcessor.parse(inputs))
								.fun(request.isFunction())
								.pop(request.isPopulation())
								.str(request.isStructure())
								.assembly(request.getAssembly())
								.inputBuild(inputBuild)
								.build();
						LOGGER.info("Building CSV for request {}, params {}", request.getFname());
						List<String[]> result = buildCSVResult(params, request);
						writer.writeAll(result);
					} else {
						final String id = inputId;
						final InputBuild detectedBuild = inputBuild;
						List<List<String>> partitions = Lists.partition(inputs, PARTITION_SIZE);
						IntStream.range(0, partitions.size())
								.mapToObj(i -> new AbstractMap.SimpleEntry<>(i, partitions.get(i)))
								.parallel() // this makes it a parallel stream
								.map(entry -> {
									int partitionNumber = entry.getKey();
									List<String> partition = entry.getValue();

									InputParams params = InputParams.builder()
											.id(id)
											.inputs(InputProcessor.parse(partition))
											.fun(request.isFunction())
											.pop(request.isPopulation())
											.str(request.isStructure())
											.assembly(request.getAssembly())
											.inputBuild(detectedBuild)
											.build();

									LOGGER.info("Building CSV for request {}, partition {}", request.getFname(), partitionNumber);
									return buildCSVResult(params, request);
								})
								.forEachOrdered(writer::writeAll);					}
				} catch (Exception e) {
					throw e; // error in CSV writing logic
				}
			} catch (IOException e) {
				throw e; // error if file opening fails
			}

			// Ensure the CSV file was actually created
			if (!Files.exists(csvPath)) {
				LOGGER.error("CSV file was not created at " + csvPath);
				throw new IOException("CSV file not found: " + csvPath);
			}

			// Zip CSV
			FileUtils.zipFile(csvPath.toString(), zipPath.toString());

			// Notify User
			Email.notifyUser(request);

			// Cleanup CSV File
			Files.deleteIfExists(csvPath);

		} catch (Throwable t) {
			LOGGER.error("Error processing CSV download request", t);
			Email.notifyUserErr(request, inputs);
			Email.notifyDevErr(request, inputs, t);
		}
		LOGGER.info("Finished processing download request: {}", request.getFname());
	}

	private List<String[]> buildCSVResult(InputParams params, DownloadRequest request) {
		MappingResponse response = request.getType() == InputType.PROTEIN_ACCESSION ?
				proteinInputMapping.getMappings(request.getInput(), params) :
				customInputMapping.getMapping(params);
		List<String[]> csvOutput = new ArrayList<>();

		response.getInputs().forEach(input -> {
			if (!input.isValid()) {
				csvOutput.add(getCsvDataInvalidInput(input));
				return;
			}
			if (input.getType() == Type.GENOMIC) {
				GenomicInput userInput = (GenomicInput) input;
				addGenInputMappingsToOutput(userInput, userInput, csvOutput, params);
			}
			else if (input.getType() == Type.CODING) {
				HGVSc userInput = (HGVSc) input;
				userInput.getDerivedGenomicInputs().forEach(genInput -> {
					addGenInputMappingsToOutput(userInput, genInput, csvOutput, params);
				});
			}
			else if (input.getType() == Type.PROTEIN) {
				ProteinInput userInput = (ProteinInput) input;
				userInput.getDerivedGenomicInputs().forEach(genInput -> {
					addGenInputMappingsToOutput(userInput, genInput, csvOutput, params);
				});
			}
			else if (input.getType() == Type.ID) {
				IDInput userInput = (IDInput) input;
				userInput.getDerivedGenomicInputs().forEach(genInput -> {
					addGenInputMappingsToOutput(userInput, genInput, csvOutput, params);
				});
			}
		});
		//TODO review
		// commenting invalidInputs in CSV for now
		// invalidInputs has been replaced by messages of type INFO, WARN, ERROR
		//response.getInvalidInputs().forEach(input -> csvOutput.add(getCsvDataInvalidInput(input)));
		return csvOutput;
	}

	private void addGenInputMappingsToOutput(UserInput userInput, GenomicInput genInput, List<String[]> csvOutput,
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
		if(genes.isEmpty())
			csvOutput.add(getCsvDataMappingNotFound(genInput));
		else
			genes.forEach(gene -> csvOutput.add(getCSVData(notes, gene, chr, genomicLocation, varAllele, id, input, params)));
	}

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
		return valList.toArray(String[]::new);
	}

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

	private List<String> getEnsps(List<Ensp> translatedSequences) {
		return translatedSequences.stream().map(translatedSeq -> {
			String ensts = translatedSeq.getTranscripts().stream().map(Transcript::getEnst).collect(Collectors.joining(":"));
			return translatedSeq.getEnsp() + "(" + ensts + ")";
		}).collect(Collectors.toList());
	}
	private List<String> addTranscripts(List<Transcript> transcripts) {
		return transcripts.stream().map(transcript -> transcript.getEnsp() + "(" + transcript.getEnst() + ")").collect(Collectors.toList());
	}
}
