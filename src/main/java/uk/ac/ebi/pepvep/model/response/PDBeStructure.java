package uk.ac.ebi.pepvep.model.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PDBeStructure {
	private String pdb_id;
	private int start;
	private String chain_id;
	private float resolution;
	private String experimental_method;
}
