package uk.ac.ebi.pepvep.model.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DSVClinicalSignificance {
	private String type;
	private List<String> sources; 
}
