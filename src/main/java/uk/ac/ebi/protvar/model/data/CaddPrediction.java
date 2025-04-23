package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CaddPrediction {
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
