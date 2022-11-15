package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.fetcher.*;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.*;

import java.util.List;

@Service
@AllArgsConstructor
public class APIService {

	private VariationFetcher populationFetcher;
	private ProteinFetcher proteinFetcher;
	private PDBeFetcher pdbeFetcher;
	private MappingFetcher mappingFetcher;
	private AssemblyMappingFetcher assemblyMappingFetcher;

	public GenomeProteinMapping getMapping(String chromosome, Long position, String id, String refAllele,
                                         String altAllele, boolean function, boolean variation, boolean structure) {
		List<OPTIONS> options = OptionBuilder.build(function, variation, structure);
		return mappingFetcher.getMapping(chromosome, position, id, refAllele, altAllele, options);
	}

	public MappingResponse getMappings(List<String> inputs, boolean function, boolean variation, boolean structure, String assembly) {
		if (inputs == null || inputs.isEmpty())
			return new MappingResponse();

		List<OPTIONS> options = OptionBuilder.build(function, variation, structure);
		return mappingFetcher.getMappings(inputs, options, Assembly.get(assembly));
	}

	public Protein getProtein(String accession, int position) {
		return proteinFetcher.fetch(accession, position);
	}

	public PopulationObservation getPopulationObservation(String accession, int position) {
		return populationFetcher.fetchPopulationObservation(accession, position + "-" + position);
	}

	public List<PDBeStructure> getStructure(String accession, int position) {
		return pdbeFetcher.fetchByAccession(accession, position);
	}

	public AssemblyMappingResponse getAssemblyMapping(List<String> inputs, String from, String to) {
		return assemblyMappingFetcher.getMappings(inputs, from, to);
	}
}
