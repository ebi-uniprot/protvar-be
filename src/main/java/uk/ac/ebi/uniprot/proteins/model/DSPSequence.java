package uk.ac.ebi.uniprot.proteins.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class DSPSequence implements Serializable {
	private int length;
	private String sequence;
	private String modified;
}
