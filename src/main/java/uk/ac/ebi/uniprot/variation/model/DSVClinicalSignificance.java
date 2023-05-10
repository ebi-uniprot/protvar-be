package uk.ac.ebi.uniprot.variation.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DSVClinicalSignificance implements Serializable {
	private String type;
	private List<String> sources; 
}
