package uk.ac.ebi.pepvep.model.api;

import lombok.Getter;

@Getter
public class Interaction {
	private String accession1;
	private String accession2;
	private String gene;
	private String interactor1;
	private String interactor2;
	private boolean organismDiffer;
	private long experiments;
}
