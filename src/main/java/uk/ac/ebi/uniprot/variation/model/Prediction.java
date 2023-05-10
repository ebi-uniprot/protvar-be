package uk.ac.ebi.uniprot.variation.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class Prediction implements Serializable {
	private String predictionValType;
	private String predAlgorithmNameType;
	private double score;
}
