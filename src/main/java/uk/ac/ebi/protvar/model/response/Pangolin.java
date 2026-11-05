package uk.ac.ebi.protvar.model.response;

import uk.ac.ebi.protvar.model.data.PangolinScore;

/**
 * Pangolin splice prediction for a variant in one gene. score = max(gain, |loss|); gain/loss positions are
 * relative to the variant (nt).
 */
public record Pangolin(float score, float gain, int gainPos, float loss, int lossPos) {
	public static Pangolin of(PangolinScore s) {
		float gain = noNegZero(s.getGainScore()), loss = noNegZero(s.getLossScore());
		return new Pangolin(noNegZero(Math.max(gain, -loss)), gain, s.getGainPos(), loss, s.getLossPos());
	}

	// the data has -0.0 losses (no change): show 0.0
	private static float noNegZero(float f) {
		return f == 0f ? 0f : f;
	}
}
