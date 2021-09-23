package uk.ac.ebi.pepvep.model.api;

import java.util.List;

import lombok.Data;

@Data
public class Text {
	private String value;
	private List<Evidence> evidences;
}
