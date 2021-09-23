package uk.ac.ebi.pepvep.model.api;

import lombok.Getter;

@Getter
public class Evidence {
	private String code;
	private DSPSource source;
	private String label;
}
