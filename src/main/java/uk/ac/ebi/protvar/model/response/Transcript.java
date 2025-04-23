package uk.ac.ebi.protvar.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Transcript {
	private String enst;
	private String ensp;
	private String ense;
}
