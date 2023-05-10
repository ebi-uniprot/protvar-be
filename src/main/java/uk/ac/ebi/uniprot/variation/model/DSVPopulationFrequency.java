package uk.ac.ebi.uniprot.variation.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class DSVPopulationFrequency implements Serializable {
	private String populationName;
	private double frequency;
	private String source;

}
