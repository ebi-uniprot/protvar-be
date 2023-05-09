package uk.ac.ebi.uniprot.proteins.model;

import java.util.List;

import lombok.Getter;

@Getter
public class DSPRecommendedName {
	private DSPName fullName;
	private List<DSPName> shortName;
	private List<Description> ecNumber;
}
