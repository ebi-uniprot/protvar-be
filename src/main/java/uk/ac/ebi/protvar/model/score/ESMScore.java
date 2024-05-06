package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ESMScore extends Score {
	private Double score; // ESM1b_score

	public ESMScore(String acc, Integer pos, String mt, Double score) {
		super(Name.ESM, acc, pos, null, mt);
		this.score = score;
	}
}
