package uk.ac.ebi.uniprot.proteins.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;

@Getter
public class DSPProtein implements Serializable {
	private DSPRecommendedName recommendedName;
	private List<DSPRecommendedName> alternativeName;
//	private DSPName cdAntigenName;
}
