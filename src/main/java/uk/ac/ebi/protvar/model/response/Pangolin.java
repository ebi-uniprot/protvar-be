package uk.ac.ebi.protvar.model.response;

import uk.ac.ebi.protvar.model.data.PangolinScore;

/**
 * Pangolin splice prediction for a variant in one gene. score = max(gain, |loss|); gain/loss positions are
 * relative to the variant (nt).
 */
public record Pangolin(double score, double gain, int gainPos, double loss, int lossPos) {
	public static Pangolin of(PangolinScore s) {
		return new Pangolin(Math.max(s.getGainScore(), -s.getLossScore()),
				s.getGainScore(), s.getGainPos(), s.getLossScore(), s.getLossPos());
	}
}
