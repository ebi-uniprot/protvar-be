package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.fetcher.*;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.*;

import java.util.List;

@Service
@AllArgsConstructor
public class APIService {

	private VariationFetcher variationFetcher;
	private ProteinsFetcher proteinsFetcher;
	private PDBeFetcher pdbeFetcher;
	private MappingFetcher mappingFetcher;
	private AssemblyMappingFetcher assemblyMappingFetcher;


	public MappingResponse getMappings(List<String> inputs, boolean function, boolean variation, boolean structure, String assemblyVersion) {
		if (inputs == null || inputs.isEmpty())
			return new MappingResponse();

		List<OPTIONS> options = OptionBuilder.build(function, variation, structure);
		return mappingFetcher.getMappings(inputs, options, assemblyVersion);
	}

	public Protein getProtein(String accession, int position, String variantAA) {
		Protein protein = proteinsFetcher.fetch(accession, position, variantAA);
		return protein;
	}

	public PopulationObservation getPopulationObservation(String accession, int position) {
		return variationFetcher.fetchPopulationObservation(accession, position);
	}

	public List<PDBeStructureResidue> getStructure(String accession, int position) {
		return pdbeFetcher.fetch(accession, position);
	}

	public AssemblyMappingResponse getAssemblyMapping(List<String> inputs, Assembly from, Assembly to) {
		return assemblyMappingFetcher.getMappings(inputs, from, to);
	}
}
