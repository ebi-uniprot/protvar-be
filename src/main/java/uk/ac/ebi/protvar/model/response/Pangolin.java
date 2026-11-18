package uk.ac.ebi.protvar.model.response;

import uk.ac.ebi.protvar.model.data.PangolinScore;

/**
 * Pangolin splice prediction for a variant in one gene. score = max(gain, |loss|); gain/loss positions are
 * relative to the variant (nt). label = the prediction when a gain or loss is significant, else null.
 */
public record Pangolin(float score, float gain, int gainPos, float loss, int lossPos, String label) {

	public static final String GAIN = "Splice gain predicted";
	public static final String LOSS = "Splice loss predicted";
	public static final String CHANGE = "Splice site change predicted";

	public static Pangolin of(PangolinScore s) {
		float gain = noNegZero(s.getGainScore()), loss = noNegZero(s.getLossScore());
		return new Pangolin(noNegZero(Math.max(gain, -loss)), gain, s.getGainPos(), loss, s.getLossPos(),
				label(gain, s.getGainPos(), loss, s.getLossPos()));
	}

	/** James's rule (option 1, no exon boundaries), applied to gain and loss separately. */
	static String label(float gain, int gainPos, float loss, int lossPos) {
		boolean g = significant(gain, gainPos), l = significant(Math.abs(loss), lossPos);
		return g && l ? CHANGE : g ? GAIN : l ? LOSS : null;
	}

	/** Splice site within 2 nt of the variant and score over 0.2, or within 8 nt and over 0.8. */
	static boolean significant(float score, int pos) {
		int d = Math.abs(pos);
		return (d <= 2 && score > 0.2f) || (d <= 8 && score > 0.8f);
	}

	// the data has -0.0 losses (no change): show 0.0
	private static float noNegZero(float f) {
		return f == 0f ? 0f : f;
	}
}
