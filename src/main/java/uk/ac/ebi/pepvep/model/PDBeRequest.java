package uk.ac.ebi.pepvep.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class PDBeRequest {
	private String accession;
	private List<String> positions;
}
