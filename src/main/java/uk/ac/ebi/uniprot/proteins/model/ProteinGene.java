package uk.ac.ebi.uniprot.proteins.model;

import java.util.List;

import lombok.Getter;

@Getter
public class ProteinGene {
	private DSPName name;
	private List<DSPName> synonyms;

}
