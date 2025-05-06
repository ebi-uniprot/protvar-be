package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.types.EveClass;

@Getter
@Setter
@NoArgsConstructor
public class EveScore extends Score {
	private Double score; // EVE_score
	private EveClass eveClass; // EVE_class

	public EveScore(Double score, EveClass eveClass) {
		super(Name.EVE);
		this.score = score;
		this.eveClass = eveClass;
	}

	public EveScore(String mt, Double score, Integer eveClass) {
		super(Name.EVE, mt);
		this.score = score;
		this.eveClass = EveClass.fromValue(eveClass);
	}

	public EveScore(String acc, Integer pos, String mt, Double score, Integer eveClass) {
		super(Name.EVE, acc, pos, mt);
		this.score = score;
		this.eveClass = EveClass.fromValue(eveClass);
	}

	public EveScore copy() {
		return new EveScore(score, eveClass);
	}
}
