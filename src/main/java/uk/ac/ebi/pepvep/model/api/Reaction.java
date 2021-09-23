package uk.ac.ebi.pepvep.model.api;

import java.util.List;

import lombok.Getter;

@Getter
public class Reaction {
	private String name;
	private List<DBReference> dbReferences;
	private List<Evidence> evidences;
}
