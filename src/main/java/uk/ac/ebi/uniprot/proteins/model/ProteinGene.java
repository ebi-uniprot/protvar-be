package uk.ac.ebi.uniprot.proteins.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;

@Getter
public class ProteinGene implements Serializable {
	private DSPName name;
	private List<DSPName> synonyms;

}
