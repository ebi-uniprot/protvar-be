package uk.ac.ebi.pepvep.model.api;

import java.util.List;

import lombok.Getter;

@Getter
public class DSPProtein {
	private DSPRecommendedName recommendedName;
	private List<DSPRecommendedName> alternativeName;
//	private DSPName cdAntigenName;
}
