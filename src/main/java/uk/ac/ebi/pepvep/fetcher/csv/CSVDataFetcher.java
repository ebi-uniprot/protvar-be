package uk.ac.ebi.pepvep.fetcher.csv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

import uk.ac.ebi.pepvep.model.response.*;
import uk.ac.ebi.pepvep.utils.*;
import uk.ac.ebi.pepvep.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.pepvep.fetcher.MappingFetcher;
import uk.ac.ebi.pepvep.model.UserInput;

@Service
@AllArgsConstructor
public class CSVDataFetcher {

	private final Logger logger = LoggerFactory.getLogger(CSVDataFetcher.class);

	private static final String CSV_HEADER_INPUT = "USER INPUT,CHROMOSOME,COORDINATE,ID,REF,ALT";
	private static final String CSV_HEADER_NOTES = "NOTES";
	private static final String CSV_HEADER_OUTPUT_MAPPING = "GENE,CODON CHANGE,STRAND,CADD PHRED,TRANSCRIPTS OF THE CANONICAL ISOFORM,"
		+ "MANE TRANSCRIPT,UNIPROT CANONICAL ISOFORM,ALTERNATIVE ISOFORM DETAILS,PROTEIN NAME,AMINO ACID POSITION,AMINO ACID CHANGE,CONSEQUENCES";
	private static final String CSV_HEADER_OUTPUT_FUNCTION = "RESIDUE FUNCTION (EVIDENCE),REGION FUNCTION (EVIDENCE),"
		+ "PROTEIN EXISTENCE,PROTEIN LENGTH,ENTRY LAST UPDATED,SEQUENCE LAST UPDATED,PROTEIN CATALYTIC ACTIVITY,"
		+ "PROTEIN COMPLEX,PROTEIN SUBCELLULAR LOCATION,PROTEIN FAMILY,PROTEIN INTERACTIONS - PROTEIN(GENE)";
	private static final String CSV_HEADER_OUTPUT_POPULATION = "GENOMIC LOCATION,CYTOGENETIC BAND,OTHER IDENTIFIERS FOR SAME VARIANT,"
		+ "DISEASES ASSOCIATED WITH VARIANT,VARIANTS CO-LOCATED AT SAME RESIDUE POSITION";
	private static final String CSV_HEADER_OUTPUT_STRUCTURE = "STRUCTURE";
	private static final String CSV_HEADER_OUTPUT = CSV_HEADER_OUTPUT_MAPPING + Constants.COMMA + CSV_HEADER_OUTPUT_FUNCTION + Constants.COMMA
		+ CSV_HEADER_OUTPUT_POPULATION + Constants.COMMA + CSV_HEADER_OUTPUT_STRUCTURE;

	static final String CSV_HEADER = CSV_HEADER_INPUT + Constants.COMMA + CSV_HEADER_NOTES + Constants.COMMA + CSV_HEADER_OUTPUT + System.lineSeparator();

	private static final int PAGE_SIZE = 500;

	private MappingFetcher mappingFetcher;
	private CSVFunctionDataFetcher functionDataFetcher;
	private CSVPopulationDataFetcher populationFetcher;
	private CSVStructureDataFetcher csvStructureDataFetcher;

	public void sendCSVResult(List<String> inputs, List<OPTIONS> options, String email, String jobName) throws Exception {

		List<String> inputList = new ArrayList<>();
		var zip = new CSVZipWriter();
		zip.writer.writeNext(CSV_HEADER.split(","));
		for (String line : inputs) {
			processInput(options, inputList, zip.writer, line);
		}
		if (!inputList.isEmpty()) {
			List<String[]> contentList = buildCSVResult(inputList, options);
			zip.writer.writeAll(contentList);
		}
		zip.close();
		Email.send(email, jobName, zip.path);
	}

	public void sendCSVResult(String file, List<OPTIONS> options, String email, String jobName) throws Exception {
		InputStream inputStream = new FileInputStream(file);
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String line;
		List<String> inputList = new ArrayList<>();

		var zip = new CSVZipWriter();
		zip.writer.writeNext(CSV_HEADER.split(","));
		while ((line = bufferedReader.readLine()) != null) {
			processInput(options, inputList, zip.writer, line);
		}
		if (!inputList.isEmpty()) {
			List<String[]> contentList = buildCSVResult(inputList, options);
			zip.writer.writeAll(contentList);
		}
		zip.close();
		Email.send(email, jobName, zip.path);
	}

	private void processInput(List<OPTIONS> options, List<String> inputList, CSVWriter csvOutput, String input) {
		if (inputList.size() >= PAGE_SIZE) {
			List<String[]> contentList = buildCSVResult(inputList, options);
			csvOutput.writeAll(contentList);
			inputList.clear();
		}
		inputList.add(input);
	}

	public void downloadCSVResult(List<String> inputs, List<OPTIONS> options, HttpServletResponse response) throws IOException {
		PrintWriter outStream = response.getWriter();
		CSVWriter writer = new CSVWriter(new BufferedWriter(outStream));
		outStream.write(CSV_HEADER);
		List<String[]> contentList = buildCSVResult(inputs, options);
		writer.writeAll(contentList);
		writer.flush();
		writer.close();

	}

	private List<String[]> buildCSVResult(List<String> inputList, List<OPTIONS> options) {
		MappingResponse response = mappingFetcher.getMappings(inputList, options);
		List<String[]> csvOutput = new ArrayList<>();

		response.getMappings().forEach(mapping -> {
			String chr = mapping.getChromosome();
			Long genomicLocation = mapping.getGeneCoordinateStart();
			String varAllele = mapping.getVariantAllele();
			String id = mapping.getId();
			String input = mapping.getInput();
			var genes = mapping.getGenes();
			if(genes.isEmpty())
				csvOutput.add(getCsvDataMappingNotFound(input));
			else
				genes.forEach(gene -> csvOutput.add(getCSVData(gene, chr, genomicLocation, varAllele, id, input, options)));
		});
		response.getInvalidInputs().forEach(input -> csvOutput.add(getCsvDataInvalidInput(input)));
		return csvOutput;
	}

	String[] getCsvDataMappingNotFound(String input){
		UserInput p = UserInput.getInput(input);
		return concatNaOutputCols(List.of(input, p.getChromosome(), p.getStart().toString(), CSVUtils.getValOrNA(p.getId()),
			p.getRef(), p.getAlt(), Constants.NOTE_MAPPING_NOT_FOUND));
	}

	String[] getCsvDataInvalidInput(UserInput input){
		return concatNaOutputCols(List.of(input.getInputString(), input.getChromosome(), input.getStart().toString(), input.getId(),
			input.getRef(), input.getAlt(), input.getInvalidReason()));
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
