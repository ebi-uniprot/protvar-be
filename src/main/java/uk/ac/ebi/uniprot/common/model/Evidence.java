package uk.ac.ebi.uniprot.common.model;

import lombok.Getter;

@Getter
public class Evidence {
	private String code;
	private DSPSource source;
	private String label;
}
