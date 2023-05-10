package uk.ac.ebi.uniprot.proteins.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class DSPInfo implements Serializable {
	private String type;
	private String created;
	private String modified;
}
