package uk.ac.ebi.pepvep.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Transcript {
	private String enst;
	private String ense;
}
