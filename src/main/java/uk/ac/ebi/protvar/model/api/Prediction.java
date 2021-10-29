package uk.ac.ebi.protvar.model.api;

import lombok.Getter;

@Getter
public class Prediction {
	private String predictionValType;
	private String predAlgorithmNameType;
	private double score;
}
