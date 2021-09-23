package uk.ac.ebi.pepvep.model.api;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataServiceProtein {
	private String accession;
	private String id;
	private String proteinExistence;
	private DSPInfo info;
	private DSPProtein protein;
	private List<ProteinGene> gene;
	private List<DSPComment> comments;
	private List<DBReference> dbReferences;
	private List<ProteinFeature> features;
	private DSPSequence sequence;
}
