package uk.ac.ebi.pepvep.model.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Ensp {
	private String ensp;
	private List<Transcript> transcripts;
}