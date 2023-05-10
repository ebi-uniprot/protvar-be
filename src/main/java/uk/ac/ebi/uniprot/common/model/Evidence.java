package uk.ac.ebi.uniprot.common.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class Evidence implements Serializable {
	private String code;
	private DSPSource source;
	private String label;
}
