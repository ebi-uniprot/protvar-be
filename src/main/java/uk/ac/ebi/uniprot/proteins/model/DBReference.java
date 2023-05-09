package uk.ac.ebi.uniprot.proteins.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DBReference {
	private String id;
	private String type;
	private Object properties;

	
}
