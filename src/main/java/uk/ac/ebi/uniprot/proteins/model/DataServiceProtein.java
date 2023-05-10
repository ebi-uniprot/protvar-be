package uk.ac.ebi.uniprot.proteins.model;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataServiceProtein implements Serializable {
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
