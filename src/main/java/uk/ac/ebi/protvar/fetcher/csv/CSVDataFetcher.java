package uk.ac.ebi.protvar.fetcher.csv;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.utils.*;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.model.UserInput;

@Service
@AllArgsConstructor
public class CSVDataFetcher {

	private final Logger logger = LoggerFactory.getLogger(CSVDataFetcher.class);

	private static final String CSV_HEADER_INPUT = "User_input,Chromosome,Coordinate,ID,Reference_allele,Alternative_allele";
	private static final String CSV_HEADER_NOTES = "Notes";
	private static final String CSV_HEADER_OUTPUT_MAPPING = "Gene,Codon_change,Strand,CADD_phred_like_score,"
		+ "Canonical_isoform_transcripts,MANE_transcript,Uniprot_canonical_isoform_(non_canonical),"
		+ "Alternative_isoform_mappings,Protein_name,Amino_acid_position,Amino_acid_change,Consequences";
	private static final String CSV_HEADER_OUTPUT_FUNCTION = "Residue_function_(evidence),Region_function_(evidence),"
		+ "Protein_existence_evidence,Protein_length,Entry_last_updated,Sequence_last_updated,Protein_catalytic_activity,"
		+ "Protein_complex,Protein_sub_cellular_location,Protein_family,Protein_interactions_PROTEIN(gene)";
	private static final String CSV_HEADER_OUTPUT_POPULATION = "Genomic_location,Cytogenetic_band,Other_identifiers_for_the_variant,"
		+ "Diseases_associated_with_variant,Variants_colocated_at_residue_position";
	private static final String CSV_HEADER_OUTPUT_STRUCTURE = "Position_in_structures";
	private static final String CSV_HEADER_OUTPUT = CSV_HEADER_OUTPUT_MAPPING + Constants.COMMA + CSV_HEADER_OUTPUT_FUNCTION + Constants.COMMA
		+ CSV_HEADER_OUTPUT_POPULATION + Constants.COMMA + CSV_HEADER_OUTPUT_STRUCTURE;

	static final String CSV_HEADER = CSV_HEADER_INPUT + Constants.COMMA + CSV_HEADER_NOTES + Constants.COMMA + CSV_HEADER_OUTPUT;

	private static final int PAGE_SIZE = 500;

	private MappingFetcher mappingFetcher;
	private CSVFunctionDataFetcher functionDataFetcher;
	private CSVPopulationDataFetcher populationFetcher;
	private CSVStructureDataFetcher csvStructureDataFetcher;

	public void sendCSVResult(List<String> inputs, List<OPTIONS> options, String email, String jobName) throws Exception {
		try {
			sendCSVResult(inputs.stream(), options, email, jobName);
		} catch (Exception e) {
			Email.sendErr(email, jobName, inputs);
			throw reportError(email, jobName, e);
		}
	}

	public void sendCSVResult(Path path, List<OPTIONS> options, String email, String jobName) throws Exception {
		try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile())))) {
      sendCSVResult(br.lines(), options, email, jobName);
    }catch (Exception e) {
			Email.sendErr(email, jobName, path);
			throw reportError(email, jobName, e);
		}
	}

	private Exception reportError(String email, String jobName, Exception e) {
		var detail = "Download failed user:" + email + " job:" + jobName;
		logger.error(detail, e);
		Email.reportException("user:" + email + " job:" + jobName, detail, e);
		return new Exception("Your request failed, check your email for details");
	}

	private void sendCSVResult(Stream<String> inputs, List<OPTIONS> options, String email, String jobName) throws Exception {
		List<String> inputList = new ArrayList<>();
		var zip = new CSVZipWriter();
		zip.writer.writeNext(CSV_HEADER.split(","));
		inputs.forEach(line -> processInput(options, inputList, zip.writer, line));
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
		writer.writeNext(CSV_HEADER.split(","));
		List<String[]> contentList = buildCSVResult(inputs, options);
		writer.writeAll(contentList);
		writer.flush();
		writer.close();
	}

	private List<String[]> buildCSVResult(List<String> inputList, List<OPTIONS> options) {
		MappingResponse response = mappingFetcher.getMappings(inputList, options, null);
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
		return concatNaOutputCols(List.of(input.getFormattedInputString(), input.getChromosome(), input.getStart().toString(), input.getId(),
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
