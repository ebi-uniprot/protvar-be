package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import uk.ac.ebi.protvar.types.EveClass;

@Getter
public class EveScore extends Score {
	private Double score; // EVE_score
	private EveClass eveClass; // EVE_class

	// Full constructor
	public EveScore(String acc, Integer pos, String mt, Double score, EveClass eveClass) {
		super(ScoreType.EVE);
		this.acc = acc;
		this.pos = pos;
		this.mt = mt;
		this.score = score;
		this.eveClass = eveClass;
	}

	// Minimal constructor
	public EveScore(String mt, Double score, EveClass eveClass) {
		super(ScoreType.EVE);
		this.mt = mt;
		this.score = score;
		this.eveClass = eveClass;
	}

	@Override
	public EveScore copySubclassFields() {
		return new EveScore(null, this.score, this.eveClass);
	}
}
