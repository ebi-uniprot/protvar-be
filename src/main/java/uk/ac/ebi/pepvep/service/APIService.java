package uk.ac.ebi.pepvep.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.pepvep.fetcher.MappingFetcher;
import uk.ac.ebi.pepvep.fetcher.PDBeFetcher;
import uk.ac.ebi.pepvep.fetcher.ProteinFetcher;
import uk.ac.ebi.pepvep.fetcher.VariationFetcher;
import uk.ac.ebi.pepvep.model.response.GenomeProteinMapping;
import uk.ac.ebi.pepvep.model.response.MappingResponse;
import uk.ac.ebi.pepvep.model.response.PopulationObservation;
import uk.ac.ebi.pepvep.model.response.Protein;
import uk.ac.ebi.pepvep.builder.OptionBuilder;
import uk.ac.ebi.pepvep.builder.OptionBuilder.OPTIONS;

import java.util.List;

@Service
@AllArgsConstructor
public class APIService {

	private VariationFetcher variationFetcher;
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

	public Protein getProtein(String accession, int position) {
		return proteinFetcher.fetch(accession, position);
	}

	public PopulationObservation getVariation(String accession, int position) {
		return variationFetcher.fetchPopulationObservation(accession, position + "-" + position);
	}

	public Object getStructure(String accession, int position) {
		return pdbeFetcher.fetchByAccession(accession, position);
	}
}
