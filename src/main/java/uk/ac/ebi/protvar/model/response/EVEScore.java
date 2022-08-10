package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EVEScore {
	public EVEScore(String accession, Integer position, String wtAA, String mtAA, Double score,
                    Integer cls) {
		super();
		this.accession = accession;
		this.position = position;
		this.wtAA = wtAA;
		this.mtAA = mtAA;
		this.score = score;
		this.eveClass = EVEClass.fromNum(cls);
	}
	private String accession;
	private Integer position;
	private String wtAA;
	private String mtAA;
	private Double score;
	private EVEClass eveClass;

	public String getGroupBy() {
		return this.accession+"-"+this.getPosition()+"-"+this.getWtAA();
	}
}
