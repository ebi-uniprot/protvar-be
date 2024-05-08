package uk.ac.ebi.protvar.model.score;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.utils.Commons;

@Getter
@Setter
public class ConservScore extends Score {
	private Double score; // AAconservation_normalised

	public ConservScore(Double score) {
		super(Name.CONSERV);
		this.score = score;
	}

	public ConservScore(String mt, Double score) {
		super(Name.CONSERV, mt);
		this.score = score;
	}

	public ConservScore(String acc, Integer pos, String mt, Double score) {
		super(Name.CONSERV, acc, pos, mt);
		this.score = score;
	}

	public ConservScore copy() {
		return new ConservScore(score);
	}

	@JsonIgnore
	// NOTE: different from other scores
	// doesn't include mt (or wt - not needed since acc-pos combination is enough)
	public String getGroupBy() {
		return Commons.joinWithDash(name, acc, pos);
	}
}
