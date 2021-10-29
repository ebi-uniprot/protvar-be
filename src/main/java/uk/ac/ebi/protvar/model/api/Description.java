package uk.ac.ebi.protvar.model.api;

import java.util.List;

import lombok.Getter;

@Getter
public class Description {
	private String value;
	private List<Evidence> evidences;
}
