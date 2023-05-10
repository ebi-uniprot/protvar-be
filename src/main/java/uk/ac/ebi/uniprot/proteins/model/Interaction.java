package uk.ac.ebi.uniprot.proteins.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class Interaction implements Serializable {
	private String accession1;
	private String accession2;
	private String gene;
	private String interactor1;
	private String interactor2;
	private boolean organismDiffer;
	private long experiments;
}
