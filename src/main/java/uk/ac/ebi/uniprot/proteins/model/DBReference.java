package uk.ac.ebi.uniprot.proteins.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Properties;

@Getter
@Setter
public class DBReference implements Serializable {
	private String id;
	private String type;
	private Properties properties;


}
