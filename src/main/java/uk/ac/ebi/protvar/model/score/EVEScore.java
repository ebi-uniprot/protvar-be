package uk.ac.ebi.protvar.model.score;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EVEScore extends Score {
	private Double score; // EVE_score
	private EVEClass eveClass; // EVE_class

	public EVEScore(String acc, Integer pos, String wt, String mt, Double score,
					Integer eveClass) {
		super(Name.EVE, acc, pos, wt, mt);
		this.score = score;
		this.eveClass = EVEClass.fromNum(eveClass);
	}

	@JsonIgnore
	public String getGroupBy() {
		return this.acc+"-"+this.getPos()+"-"+this.getWt();
	}
}
