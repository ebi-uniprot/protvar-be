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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.model.data.EVEClass;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.utils.*;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;

@Service
@AllArgsConstructor
public class CSVDataFetcher {

	private final Logger logger = LoggerFactory.getLogger(CSVDataFetcher.class);

	private static final String CSV_HEADER_INPUT = "User_input,Chromosome,Coordinate,ID,Reference_allele,Alternative_allele";
	private static final String CSV_HEADER_NOTES = "Notes";
	private static final String CSV_HEADER_OUTPUT_MAPPING = "Gene,Codon_change,Strand,CADD_phred_like_score,"
		+ "Canonical_isoform_transcripts,MANE_transcript,Uniprot_canonical_isoform_(non_canonical),"
		+ "Alternative_isoform_mappings,Protein_name,Amino_acid_position,Amino_acid_change,Consequences,EVE_score(class)";
	private static final String CSV_HEADER_OUTPUT_FUNCTION = "Residue_function_(evidence),Region_function_(evidence),"
		+ "Protein_existence_evidence,Protein_length,Entry_last_updated,Sequence_last_updated,Protein_catalytic_activity,"
		+ "Protein_complex,Protein_sub_cellular_location,Protein_family,Protein_interactions_PROTEIN(gene),"
		+ "Predicted_pockets(energy;per_vol;score;resids),Predicted_interactions(chainA-chainB;a_resids;b_resids;pDockQ),"
		+ "Foldx_prediction(foldxDdq;plddt),Conservation_score";
	private static final String CSV_HEADER_OUTPUT_POPULATION = "Genomic_location,Cytogenetic_band,Other_identifiers_for_the_variant,"
		+ "Diseases_associated_with_variant,Variants_colocated_at_residue_position";
	private static final String CSV_HEADER_OUTPUT_STRUCTURE = "Position_in_structures";
	private static final String CSV_HEADER_OUTPUT = CSV_HEADER_OUTPUT_MAPPING + Constants.COMMA + CSV_HEADER_OUTPUT_FUNCTION + Constants.COMMA
		+ CSV_HEADER_OUTPUT_POPULATION + Constants.COMMA + CSV_HEADER_OUTPUT_STRUCTURE;

	static final String CSV_HEADER = CSV_HEADER_INPUT + Constants.COMMA + CSV_HEADER_NOTES + Constants.COMMA + CSV_HEADER_OUTPUT;

	private static final int PAGE_SIZE = 1000;

	private MappingFetcher mappingFetcher;
	private CSVFunctionDataFetcher functionDataFetcher;
	private CSVPopulationDataFetcher populationFetcher;
	private CSVStructureDataFetcher csvStructureDataFetcher;

	private String downloadDir;


	@Async
	public void writeCSVResult(List<String> inputs, List<OPTIONS> options, String email, String jobName, Download download) {
		try {
			processInput(inputs, options, email, jobName, download);
		} catch (Exception e) {
			Email.sendErr(email, jobName, inputs);
			reportError(email, jobName, e);
		}
	}

	@Async
	public void writeCSVResult(Path path, List<OPTIONS> options, String email, String jobName, Download download) {
		try (Stream<String> lines = Files.lines(path)) {
			processInput(lines.collect(Collectors.toList()), options, email, jobName, download);
		} catch (Exception e) {
			Email.sendErr(email, jobName, path);
			reportError(email, jobName, e);
		} finally {
			FileUtils.tryDelete(path);
		}
	}

	private void reportError(String email, String jobName, Exception e) {
		var detail = "Download job failed:" + jobName;
		logger.error(detail, e);
		Email.reportException(" job:" + jobName, detail, e);
		//return new Exception("Your request failed, check your email for details");
	}

	/*
	private void zipWriteCSVResult(Stream<String> inputs, List<OPTIONS> options, String email, String jobName, Download download) throws Exception {
		List<String> inputList = new ArrayList<>();
		var zip = new CSVZipWriter(downloadDir, download.getDownloadId());
		zip.writer.writeNext(CSV_HEADER.split(","));
		inputs.forEach(line -> processInput(options, inputList, zip.writer, line));
		if (!inputList.isEmpty()) {
			List<String[]> contentList = buildCSVResult(inputList, options);
			zip.writer.writeAll(contentList);
		}
		zip.close();
		if (Commons.notNullNotEmpty(email))
			Email.notify(email, jobName, download.getUrl());
	}*/
	private void processInput(List<String> inputs, List<OPTIONS> options, String email, String jobName, Download download) throws Exception {
		List<List<String>> inputPartitions = Lists.partition(inputs, PAGE_SIZE);

		Path filePath = Paths.get(downloadDir, download.getDownloadId() + ".csv");
		String fileName = filePath.toString();

		// write csv
		try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(filePath));
			 CSVWriter writer = new CSVWriter(outputStreamWriter)) {
			writer.writeNext(CSV_HEADER.split(","));
			// v1
			//inputPartitions.parallelStream().forEachOrdered(partition -> {
			//	List<String[]> resultList = buildCSVResult(partition, options);
			//	writer.writeAll(resultList);
			//});
			// v2
			//List<List <String[]>> resultParts = inputPartitions.parallelStream().map(partition -> buildCSVResult(partition, options)).collect(Collectors.toList());
			//resultParts.stream().forEach(writer::writeAll);
			// v3
			inputPartitions.parallelStream().map(partition -> buildCSVResult(partition, options))
					.forEach(writer::writeAll);
		}

		// zip csv
		FileUtils.zipFile(fileName, fileName + ".zip");

		if (Commons.notNullNotEmpty(email))
			Email.notify(email, jobName, download.getUrl());
	}

	public void downloadCSVResult(List<String> inputs, List<OPTIONS> options, HttpServletResponse response) throws IOException {
		PrintWriter outStream = response.getWriter();
		CSVWriter writer = new CSVWriter(new BufferedWriter(outStream));
		writer.writeNext(CSV_HEADER.split(","));
		List<String[]> contentList = buildCSVResult(inputs, options);
		writer.writeAll(contentList);
		writer.flush();
		writer.close();
	}

	private List<String[]> buildCSVResult(List<String> inputList, List<OPTIONS> options) {
		MappingResponse response = mappingFetcher.getMappings(inputList, options, null);
		List<String[]> csvOutput = new ArrayList<>();

		response.getInputs().forEach(input -> {
			if (input.getType() == InputType.GEN) {
				GenomicInput genInput = (GenomicInput) input;
				addGenInputMappingsToOutput(genInput, genInput.getMappings(), csvOutput, options);
			}
			else if (input.getType() == InputType.PRO) {
				ProteinInput proInput = (ProteinInput) input;
				proInput.getDerivedGenomicInputs().forEach(gInput -> {
					addGenInputMappingsToOutput(gInput, gInput.getMappings(), csvOutput, options);
				});
			}
			else if (input.getType() == InputType.RS) {
				RSInput rsInput = (RSInput) input;
				rsInput.getDerivedGenomicInputs().forEach(gInput -> {
					addGenInputMappingsToOutput(gInput, gInput.getMappings(), csvOutput, options);
				});
			}
		});
		//TODO review
		// commenting invalidInputs in CSV for now
		// invalidInputs has been replaced by messages of type INFO, WARN, ERROR
		//response.getInvalidInputs().forEach(input -> csvOutput.add(getCsvDataInvalidInput(input)));
		return csvOutput;
	}

	private void addGenInputMappingsToOutput(GenomicInput gInput, List<GenomeProteinMapping> mappings, List<String[]> csvOutput, List<OPTIONS> options) {

		String chr = gInput.getChr();
		Long genomicLocation = gInput.getPos();
		String varAllele = gInput.getAlt();
		String id = gInput.getId();
		String input = gInput.getInputStr();
		mappings.forEach(mapping -> {
			var genes = mapping.getGenes();
			if(genes.isEmpty())
				csvOutput.add(getCsvDataMappingNotFound(input));
			else
				genes.forEach(gene -> csvOutput.add(getCSVData(gene, chr, genomicLocation, varAllele, id, input, options)));
		});
	}

	String[] getCsvDataMappingNotFound(String input){
		UserInput p = UserInput.getInput(input);
		return concatNaOutputCols(List.of(input, Constants.NOTE_MAPPING_NOT_FOUND));
	}

	String[] getCsvDataInvalidInput(UserInput input){
		return concatNaOutputCols(List.of(input.getInputStr(), String.join("|", input.getErrors())));
	}

	private String[] concatNaOutputCols(List<String> inputAndNotes){
		var valList = new ArrayList<>(inputAndNotes);
		valList.addAll(Arrays.stream(CSV_HEADER_OUTPUT.split(Constants.COMMA)).map(ignore -> Constants.NA).collect(Collectors.toList()));
		return valList.toArray(String[]::new);
	}

	private String[] getCSVData(Gene gene, String chr, Long genomicLocation, String varAllele, String id, String input, List<OPTIONS> options) {
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
			varAllele, Constants.NA, gene.getGeneName(), mapping.getCodonChange(), strand, cadd,
			ensps.toString(), Constants.NA,mapping.getAccession(), CSVUtils.getValOrNA(alternateInformDetails), mapping.getProteinName(),
			String.valueOf(mapping.getIsoformPosition()), mapping.getAminoAcidChange(),
			mapping.getConsequences(),getEveScore(mapping)));

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

	private String getEveScore(IsoFormMapping mapping) {
		if (mapping.getEveScore() == null) return Constants.NA;
		StringBuilder eve = new StringBuilder();
		eve.append(mapping.getEveScore().toString());
		if (mapping.getEveClass() != null) {
			EVEClass eveClass = EVEClass.fromNum(mapping.getEveClass());
			if (eveClass != null && eveClass.getVal() != null) {
				eve.append(" (").append(eveClass.getVal()).append(")");
			}
		}
		return eve.toString();
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
