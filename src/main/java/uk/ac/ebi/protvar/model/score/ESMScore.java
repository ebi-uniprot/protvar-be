package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ESMScore extends Score {
	private Double score; // ESM1b_score

	public ESMScore(Double score) {
		super(Name.ESM);
		this.score = score;
	}

	public ESMScore(String mt, Double score) {
		super(Name.ESM, mt);
		this.score = score;
	}

	public ESMScore(String acc, Integer pos, String mt, Double score) {
		super(Name.ESM, acc, pos, mt);
		this.score = score;
	}

	public ESMScore copy() {
		return new ESMScore(score);
	}
}
