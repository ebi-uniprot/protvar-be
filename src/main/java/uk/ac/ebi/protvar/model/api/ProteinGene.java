package uk.ac.ebi.protvar.model.api;

import java.util.List;

import lombok.Getter;

@Getter
public class ProteinGene {
	private DSPName name;
	private List<DSPName> synonyms;

}
