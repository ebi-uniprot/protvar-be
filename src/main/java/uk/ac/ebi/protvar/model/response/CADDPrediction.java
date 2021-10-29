package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CADDPrediction {
	public CADDPrediction(String chromosome, Long position, String referenceAllele, String altAllele, double rawScore,
			double score) {
		super();
		this.chromosome = chromosome;
		this.position = position;
		this.referenceAllele = referenceAllele;
		this.altAllele = altAllele;
		this.rawScore = rawScore;
		this.score = score;
	}
	private String chromosome;
	private Long position;
	private String referenceAllele;
	private String altAllele;
	private double rawScore;
	private double score;
	
	public String getGroupBy() {
		return this.chromosome+"-"+this.getPosition();
	}
}
