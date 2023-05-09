package uk.ac.ebi.uniprot.proteins.model;

import java.util.List;

import lombok.Getter;

@Getter
public class DSPProtein {
	private DSPRecommendedName recommendedName;
	private List<DSPRecommendedName> alternativeName;
//	private DSPName cdAntigenName;
}
