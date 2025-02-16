package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CADDPrediction {
	private String chromosome;
	private Integer position;
	private String referenceAllele;
	private String altAllele;
	private double rawScore;
	private double score;
	
	public String getGroupBy() {
		return this.chromosome+"-"+this.getPosition();
	}
}
