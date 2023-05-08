package uk.ac.ebi.protvar.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.protvar.builder.OptionBuilder;
import uk.ac.ebi.protvar.builder.OptionBuilder.OPTIONS;
import uk.ac.ebi.protvar.fetcher.*;
import uk.ac.ebi.protvar.model.data.ConservScore;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;

import java.util.List;

@Service
@AllArgsConstructor
public class APIService {

	private VariationFetcher populationFetcher;
	private ProteinFetcher proteinFetcher;
	private PDBeFetcher pdbeFetcher;
	private MappingFetcher mappingFetcher;
	private AssemblyMappingFetcher assemblyMappingFetcher;

	private ProtVarDataRepo protVarDataRepo;


	public MappingResponse getMappings(List<String> inputs, boolean function, boolean variation, boolean structure, String assemblyVersion) {
		if (inputs == null || inputs.isEmpty())
			return new MappingResponse();

		List<OPTIONS> options = OptionBuilder.build(function, variation, structure);
		return mappingFetcher.getMappings(inputs, options, assemblyVersion);
	}

	public Protein getProtein(String accession, int position) {
		Protein protein = proteinFetcher.fetch(accession, position);
		if (protein != null) {
			protein.setPockets(protVarDataRepo.getPockets(accession, position));
			protein.setInteractions(protVarDataRepo.getInteractions(accession, position));
			protein.setFoldxs(protVarDataRepo.getFoldxs(accession, position));
			List<ConservScore> conservScores = protVarDataRepo.getConservScores(accession, position);
			if (conservScores != null && conservScores.size() == 1) {
				protein.setConservScore(conservScores.get(0).getScore());
			}
		}

		return protein;
	}

	public PopulationObservation getPopulationObservation(String accession, int position) {
		return populationFetcher.fetchPopulationObservation(accession, position + "-" + position);
	}

	public List<PDBeStructureResidue> getStructure(String accession, int position) {
		return pdbeFetcher.fetch(accession, position);
	}

	public AssemblyMappingResponse getAssemblyMapping(List<String> inputs, Assembly from, Assembly to) {
		return assemblyMappingFetcher.getMappings(inputs, from, to);
	}
}
