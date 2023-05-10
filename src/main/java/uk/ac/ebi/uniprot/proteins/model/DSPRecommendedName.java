package uk.ac.ebi.uniprot.proteins.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;

@Getter
public class DSPRecommendedName implements Serializable {
	private DSPName fullName;
	private List<DSPName> shortName;
	private List<Description> ecNumber;
}
