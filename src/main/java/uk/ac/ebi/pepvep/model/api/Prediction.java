package uk.ac.ebi.pepvep.model.api;

import lombok.Getter;

@Getter
public class Prediction {
	private String predictionValType;
	private String predAlgorithmNameType;
	private double score;
}
