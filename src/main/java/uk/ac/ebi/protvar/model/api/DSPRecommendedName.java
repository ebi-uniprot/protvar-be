package uk.ac.ebi.protvar.model.api;

import java.util.List;

import lombok.Getter;

@Getter
public class DSPRecommendedName {
	private DSPName fullName;
	private List<DSPName> shortName;
	private List<Description> ecNumber;
}
