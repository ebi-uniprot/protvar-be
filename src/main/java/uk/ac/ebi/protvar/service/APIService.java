package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.fetcher.PDBeFetcher;
import uk.ac.ebi.protvar.fetcher.ProteinFetcher;
import uk.ac.ebi.protvar.fetcher.VariationFetcher;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;

import java.util.List;

@Service
@AllArgsConstructor
public class APIService {

	private VariationFetcher populationFetcher;
	private ProteinFetcher proteinFetcher;
	private PDBeFetcher pdbeFetcher;
	private MappingFetcher mappingFetcher;

	public GenomeProteinMapping getMapping(String chromosome, Long position, String id, String refAllele,
                                         String altAllele, boolean function, boolean variation, boolean structure) {
		List<OPTIONS> options = OptionBuilder.build(function, variation, structure);
		return mappingFetcher.getMapping(chromosome, position, id, refAllele, altAllele, options);
	}

	public MappingResponse getMappings(List<String> inputs, boolean function, boolean variation, boolean structure) {
		if (inputs == null || inputs.isEmpty())
			return new MappingResponse();

		List<OPTIONS> options = OptionBuilder.build(function, variation, structure);
		return mappingFetcher.getMappings(inputs, options);
	}

	public MappingResponse getMappingsByProteinAccessionsAndPositions(List<String> inputs, boolean function, boolean variation, boolean structure) {
		if (inputs == null || inputs.isEmpty())
			return new MappingResponse();
		List<OPTIONS> options = OptionBuilder.build(function, variation, structure);
		return mappingFetcher.getMappingsByProteinAccessionsAndPositions(inputs, options);
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
}
