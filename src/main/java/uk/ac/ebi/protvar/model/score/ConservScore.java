package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConservScore extends Score {
	private Double score; // AAconservation_normalised

	public ConservScore(String acc, Integer pos, String wt, Double score) {
		super(Name.CONSERV, acc, pos, wt, null);
		this.score = score;
	}
}
