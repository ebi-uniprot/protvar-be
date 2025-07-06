package uk.ac.ebi.protvar.model.score;

import lombok.Getter;

@Getter
public class EsmScore extends Score {
	private Double score; // ESM1b_score

	// Full constructor
	public EsmScore(String acc, Integer pos, String mt, Double score) {
		super(ScoreType.ESM);
		this.acc = acc;
		this.pos = pos;
		this.mt = mt;
		this.score = score;
	}

	// Minimal constructor
	public EsmScore(String mt, Double score) {
		super(ScoreType.ESM);
		this.mt = mt;
		this.score = score;
	}

	@Override
	public EsmScore copySubclassFields() {
		return new EsmScore(null, score);
	}
}
