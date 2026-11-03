package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.utils.VariantKey;

/**
 * Pangolin splice score (rel_<R>_pangolin_score): one row per coding SNV per gene, as genes can overlap.
 * Gain/loss = max increase/decrease of splice-site usage within ±50 nt; positions are relative to the variant.
 */
@Getter
@Setter
@NoArgsConstructor
public class PangolinScore {
	private String chromosome;
	private Integer position;
	private String referenceAllele;
	private String altAllele;
	private String geneId; // unversioned ENSG
	private double gainScore;
	private int gainPos;
	private double lossScore;
	private int lossPos;

	public String getVariantKey() {
		return VariantKey.genomic(this.chromosome, position);
	}
}
