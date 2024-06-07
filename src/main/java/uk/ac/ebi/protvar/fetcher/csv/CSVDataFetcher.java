package uk.ac.ebi.protvar.fetcher.csv;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
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
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.utils.*;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;

@Service
@AllArgsConstructor
public class CSVDataFetcher {

	private static final String MAPPING_NOT_FOUND = "Mapping not found";

	private static final String CSV_HEADER_INPUT = "User_input,Chromosome,Coordinate,ID,Reference_allele,Alternative_allele";
	private static final String CSV_HEADER_NOTES = "Notes";
	private static final String CSV_HEADER_OUTPUT_MAPPING = "Gene,Codon_change,Strand,CADD_phred_like_score,"
		+ "Canonical_isoform_transcripts,MANE_transcript,Uniprot_canonical_isoform_(non_canonical),"
		+ "Alternative_isoform_mappings,Protein_name,Amino_acid_position,Amino_acid_change,Consequences";
	private static final String CSV_HEADER_OUTPUT_FUNCTION = "Residue_function_(evidence),Region_function_(evidence),"
		+ "Protein_existence_evidence,Protein_length,Entry_last_updated,Sequence_last_updated,Protein_catalytic_activity,"
		+ "Protein_complex,Protein_sub_cellular_location,Protein_family,Protein_interactions_PROTEIN(gene),"
		+ "Predicted_pockets(energy;per_vol;score;resids),Predicted_interactions(chainA-chainB;a_resids;b_resids;pDockQ),"
		+ "Foldx_prediction(foldxDdg;plddt),Conservation_score,AlphaMissense_pathogenicity(class),EVE_score(class),ESM1b_score";
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
	private InputProcessor inputProcessor;

	private String downloadDir;

	public void writeCSVResult(DownloadRequest request) {
		try {
			List<OptionBuilder.OPTIONS> options = OptionBuilder.build(request.isFunction(), request.isPopulation(), request.isStructure());
			List<String> inputs = request.getFile() == null ? request.getInputs() :
					Files.lines(request.getFile()).collect(Collectors.toList());

			List<List<String>> inputPartitions = Lists.partition(inputs, PAGE_SIZE);

			Path filePath = Paths.get(downloadDir, request.getId() + ".csv");
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
				inputPartitions.parallelStream().map(partition -> buildCSVResult(partition, request.getAssembly(), options))
						.forEach(writer::writeAll);
			}

			// zip csv
			FileUtils.zipFile(fileName, fileName + ".zip");
			// results ready
			Email.notifyUser(request);
		} catch (Throwable t) {
			Email.notifyUserErr(request);
			Email.notifyDevErr(request, t);
		} finally {
			if (request.getFile() != null)
				FileUtils.tryDelete(request.getFile());
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
				GenomicInput genInput = (GenomicInput) input;
				addGenInputMappingsToOutput(genInput, genInput.getMappings(), csvOutput, options);
			}
			else if (input.getType() == Type.CODING) {
				HGVSc cDNAInput = (HGVSc) input;
				cDNAInput.getDerivedGenomicInputs().forEach(gInput -> {
					addGenInputMappingsToOutput(gInput, gInput.getMappings(), csvOutput, options);
				});
			}
			else if (input.getType() == Type.PROTEIN) {
				ProteinInput proInput = (ProteinInput) input;
				proInput.getDerivedGenomicInputs().forEach(gInput -> {
					addGenInputMappingsToOutput(gInput, gInput.getMappings(), csvOutput, options);
				});
			}
			else if (input.getType() == Type.ID) {
				IDInput idInput = (IDInput) input;
				idInput.getDerivedGenomicInputs().forEach(gInput -> {
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
		Integer genomicLocation = gInput.getPos();
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
		//UserInput p = UserInput.getInput(input);
		return concatNaOutputCols(List.of(input, MAPPING_NOT_FOUND));
	}

	String[] getCsvDataInvalidInput(UserInput input){
		return concatNaOutputCols(List.of(input.getInputStr(), String.join("|", input.getErrors())));
	}

	private String[] concatNaOutputCols(List<String> inputAndNotes){
		var valList = new ArrayList<>(inputAndNotes);
		valList.addAll(Arrays.stream(CSV_HEADER_OUTPUT.split(Constants.COMMA)).map(ignore -> Constants.NA).toList());
		return valList.toArray(String[]::new);
	}

	private String[] getCSVData(Gene gene, String chr, Integer genomicLocation, String varAllele, String id, String input, List<OPTIONS> options) {
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
