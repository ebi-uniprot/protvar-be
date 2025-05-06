package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EsmScore extends Score {
	private Double score; // ESM1b_score

	public EsmScore(Double score) {
		super(Name.ESM);
		this.score = score;
	}

	public EsmScore(String mt, Double score) {
		super(Name.ESM, mt);
		this.score = score;
	}

	public EsmScore(String acc, Integer pos, String mt, Double score) {
		super(Name.ESM, acc, pos, mt);
		this.score = score;
	}

	public EsmScore copy() {
		return new EsmScore(score);
	}
}
