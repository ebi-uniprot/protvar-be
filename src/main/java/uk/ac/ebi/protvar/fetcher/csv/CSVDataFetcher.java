package uk.ac.ebi.protvar.fetcher.csv;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.processor.InputProcessor;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.IDInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.ResultType;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.service.PagedMappingService;
import uk.ac.ebi.protvar.utils.*;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;

import static uk.ac.ebi.protvar.config.PagedMapping.DEFAULT_PAGE_SIZE;

@Service
@AllArgsConstructor
public class CSVDataFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(CSVDataFetcher.class);

	private static final String MAPPING_NOT_FOUND = "Mapping not found";

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

	private MappingFetcher mappingFetcher;
	private CSVFunctionDataFetcher functionDataFetcher;
	private CSVPopulationDataFetcher populationFetcher;
	private CSVStructureDataFetcher csvStructureDataFetcher;
	private InputProcessor inputProcessor;

	private ProtVarDataRepo protVarDataRepo;

	private String downloadDir;
	private RedisTemplate redisTemplate;

	public void writeCSVResult(DownloadRequest request) {
		List<String> inputs = null;
		try {
			List<OptionBuilder.OPTIONS> options = OptionBuilder.build(request.isFunction(), request.isPopulation(), request.isStructure());

			Path zipPath = Paths.get(downloadDir, request.getFname() + ".csv.zip");
			if (Files.exists(zipPath)) {
				LOGGER.warn("{} exists", zipPath.getFileName());
				return;
			}

			if (request.getType() == ResultType.PROTEIN_ACC) {
				if (request.getPage() == null) {
					inputs = protVarDataRepo.getGenInputsByAccession(request.getId(), null, null);

				} else {
					Integer pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
					inputs = protVarDataRepo.getGenInputsByAccession(request.getId(), request.getPage(), pageSize);

				}
			} else {

				if (redisTemplate.hasKey(request.getId())) {
					String originalInput = redisTemplate.opsForValue().get(request.getId()).toString();
					if (originalInput != null) {
						List<String> originalInputList = Arrays.asList(originalInput.split("\\R|,"));
						if (request.getPage() == null) {
							inputs = originalInputList;
						} else {
							Integer pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
							inputs = PagedMappingService.getPage(originalInputList, request.getPage(), pageSize);
						}
					}
				} else {
					LOGGER.warn("{} id not found", request.getId());
					return;
				}
			}

			if (inputs == null || inputs.size() == 0) {
				LOGGER.warn("no inputs to generate download file");
				return;
			}

			List<List<String>> inputPartitions = Lists.partition(inputs, PARTITION_SIZE);
			// write csv
			Path csvPath = Paths.get(downloadDir, request.getFname() + ".csv");
			try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(csvPath));
				 CSVWriter writer = new CSVWriter(outputStreamWriter)) {
				writer.writeNext(CSV_HEADER.split(","));
				// v1
				//inputPartitions.parallelStream().forEachOrdered(partition -> {
				//	List<String[]> resultList = buildCSVResult(partition, options);
				//	writer.writeAll(resultList);
				//});
				// v2
				List<List <String[]>> resultParts = inputPartitions.parallelStream()
						.map(partition -> buildCSVResult(partition, request.getAssembly(), options))
						.collect(Collectors.toList());
				resultParts.stream().forEach(writer::writeAll);
				// v3 - probably causing input order in csv to change
				//inputPartitions.parallelStream().map(partition -> buildCSVResult(partition, request.getAssembly(), options))
				//		.forEach(writer::writeAll);
			}

			// zip csv
			FileUtils.zipFile(csvPath.toString(), zipPath.toString());
			// results ready
			Email.notifyUser(request);
		} catch (Throwable t) {
			Email.notifyUserErr(request, inputs);
			Email.notifyDevErr(request, inputs, t);
		}
	}

	public void downloadCSVResult(List<String> inputs, List<OPTIONS> options, HttpServletResponse response) throws IOException {
		PrintWriter outStream = response.getWriter();
		CSVWriter writer = new CSVWriter(new BufferedWriter(outStream));
		writer.writeNext(CSV_HEADER.split(","));
		List<String[]> contentList = buildCSVResult(inputs, null, options);
		writer.writeAll(contentList);
		writer.flush();
		writer.close();
	}

	private List<String[]> buildCSVResult(List<String> inputList, String assembly, List<OPTIONS> options) {
		List<UserInput> userInputs = inputProcessor.parse(inputList);
		MappingResponse response = mappingFetcher.getMappings(userInputs, options, assembly);
		List<String[]> csvOutput = new ArrayList<>();

		response.getInputs().forEach(input -> {
			if (input.getType() == Type.GENOMIC) {
				GenomicInput userInput = (GenomicInput) input;
				addGenInputMappingsToOutput(userInput, userInput, csvOutput, options);
			}
			else if (input.getType() == Type.CODING) {
				HGVSc userInput = (HGVSc) input;
				userInput.getDerivedGenomicInputs().forEach(genInput -> {
					addGenInputMappingsToOutput(userInput, genInput, csvOutput, options);
				});
			}
			else if (input.getType() == Type.PROTEIN) {
				ProteinInput userInput = (ProteinInput) input;
				userInput.getDerivedGenomicInputs().forEach(genInput -> {
					addGenInputMappingsToOutput(userInput, genInput, csvOutput, options);
				});
			}
			else if (input.getType() == Type.ID) {
				IDInput userInput = (IDInput) input;
				userInput.getDerivedGenomicInputs().forEach(genInput -> {
					addGenInputMappingsToOutput(userInput, genInput, csvOutput, options);
				});
			}
		});
		//TODO review
		// commenting invalidInputs in CSV for now
		// invalidInputs has been replaced by messages of type INFO, WARN, ERROR
		//response.getInvalidInputs().forEach(input -> csvOutput.add(getCsvDataInvalidInput(input)));
		return csvOutput;
	}

	private void addGenInputMappingsToOutput(UserInput userInput, GenomicInput genInput, List<String[]> csvOutput, List<OPTIONS> options) {

		if (!userInput.isValid()) {
			csvOutput.add(getCsvDataInvalidInput(userInput));
			return;
		}

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

		genInput.getMappings().forEach(mapping -> {
			var genes = mapping.getGenes();
			if(genes.isEmpty())
				csvOutput.add(getCsvDataMappingNotFound(genInput));
			else
				genes.forEach(gene -> csvOutput.add(getCSVData(notes, gene, chr, genomicLocation, varAllele, id, input, options)));
		});
	}

	String[] getCsvDataMappingNotFound(GenomicInput genInput){
		List<String> valList = List.of(genInput.getInputStr(), // User_input
				strValOrNA(genInput.getChr()), // Chromosome,Coordinate,ID,Reference_allele,Alternative_allele
				intValOrNA(genInput.getPos()),
				strValOrNA(genInput.getId()),
				strValOrNA(genInput.getRef()),
				strValOrNA(genInput.getAlt())
		);
		valList.add(MAPPING_NOT_FOUND); // Notes
		valList.addAll(Collections.nCopies(CSV_HEADER_OUTPUT.split(Constants.COMMA).length, Constants.NA));
		return valList.toArray(String[]::new);
	}

	String[] getCsvDataInvalidInput(UserInput input){
		List<String> valList = List.of(input.getInputStr()); // User_input
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

	private String[] getCSVData(String notes, Gene gene, String chr, Integer genomicLocation, String varAllele, String id, String input, List<OPTIONS> options) {
		String cadd = null;
		if (gene.getCaddScore() != null)
			cadd = gene.getCaddScore().toString();
		String strand = "+";
		IsoFormMapping mapping = gene.getIsoforms().get(0);

		if (gene.isReverseStrand()) {
			strand = "-";
		}

		var alternateInformDetails = buildAlternateInformDetails(gene.getIsoforms());
		List<String> ensps = getEnsps(mapping.getTranslatedSequences());

		List<String> output = new ArrayList<>(Arrays.asList(input, chr, genomicLocation.toString(), id, gene.getRefAllele(),
			varAllele, notes, gene.getGeneName(), mapping.getCodonChange(), strand, cadd,
			ensps.toString(), Constants.NA,mapping.getAccession(), CSVUtils.getValOrNA(alternateInformDetails), mapping.getProteinName(),
			String.valueOf(mapping.getIsoformPosition()), mapping.getAminoAcidChange(),
			mapping.getConsequences()));

		if (options.contains(OPTIONS.FUNCTION) && mapping.getReferenceFunction() != null)
			output.addAll(functionDataFetcher.fetch(mapping));
		else
			addNaForNonRequestedData(output, CSV_HEADER_OUTPUT_FUNCTION);

		if (options.contains(OPTIONS.POPULATION) && mapping.getPopulationObservations() != null)
				output.addAll(populationFetcher.fetch(mapping.getPopulationObservations(), mapping.getRefAA(), mapping.getVariantAA(), genomicLocation));
		else
			addNaForNonRequestedData(output, CSV_HEADER_OUTPUT_POPULATION);

		if (options.contains(OPTIONS.STRUCTURE) && mapping.getProteinStructure() != null)
			output.add(csvStructureDataFetcher.fetch(mapping.getProteinStructure()));
		else
			addNaForNonRequestedData(output, CSV_HEADER_OUTPUT_STRUCTURE);

		return output.toArray(String[]::new);
	}

	private void addNaForNonRequestedData(List<String> output, String header) {
		for (int i = 0; i < header.split(Constants.COMMA).length; i++)
			output.add(Constants.NA);
	}

	private String buildAlternateInformDetails(List<IsoFormMapping> value) {
		List<String> isformDetails = new ArrayList<>();
		for (IsoFormMapping mapping: value) {
			if (mapping.isCanonical())
				continue;
			List<String> ensps = getEnsps(mapping.getTranslatedSequences());
			isformDetails.add(
				mapping.getAccession() + ";" +
					mapping.getIsoformPosition() + ";" +
					mapping.getAminoAcidChange() + ";" +
					mapping.getConsequences() + ";" + ensps);
		}
		return String.join("|", isformDetails);
	}

	private List<String> getEnsps(List<Ensp> translatedSequences) {
		return translatedSequences.stream().map(translatedSeq -> {
			String ensts = translatedSeq.getTranscripts().stream().map(Transcript::getEnst).collect(Collectors.joining(":"));
			return translatedSeq.getEnsp() + "(" + ensts + ")";
		}).collect(Collectors.toList());
	}
}
