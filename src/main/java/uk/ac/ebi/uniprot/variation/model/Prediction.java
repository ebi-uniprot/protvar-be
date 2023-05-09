package uk.ac.ebi.uniprot.variation.model;

import lombok.Getter;

@Getter
public class Prediction {
	private String predictionValType;
	private String predAlgorithmNameType;
	private double score;
}
