package uk.ac.ebi.protvar.model.score;

import lombok.Getter;

@Getter
public class ConservScore extends Score {
	private Double score; // AAconservation_normalised

	// Full constructor - inherited and subclass fields
	public ConservScore(String acc, Integer pos, Double score) {
		super(ScoreType.CONSERV);
		this.acc = acc;
		this.pos = pos;
		this.score = score;
	}

	// Minimal constructor - only subclass fields
	public ConservScore(Double score) {
		super(ScoreType.CONSERV);
		this.score = score;
	}

	@Override
	public ConservScore copySubclassFields() {
		return new ConservScore(this.score);
	}
}
