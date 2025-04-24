package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EVEScore extends Score {
	private Double score; // EVE_score
	private EVEClass eveClass; // EVE_class

	public EVEScore(Double score, EVEClass eveClass) {
		super(Name.EVE);
		this.score = score;
		this.eveClass = eveClass;
	}

	public EVEScore(String mt, Double score, Integer eveClass) {
		super(Name.EVE, mt);
		this.score = score;
		this.eveClass = EVEClass.fromNum(eveClass);
	}

	public EVEScore(String acc, Integer pos, String mt, Double score, Integer eveClass) {
		super(Name.EVE, acc, pos, mt);
		this.score = score;
		this.eveClass = EVEClass.fromNum(eveClass);
	}

	public EVEScore copy() {
		return new EVEScore(score, eveClass);
	}
}
