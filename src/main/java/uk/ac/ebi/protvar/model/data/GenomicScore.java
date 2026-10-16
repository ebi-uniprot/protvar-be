package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.utils.VariantKey;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenomicScore {
	private String chromosome;
	private Integer position;
	private String referenceAllele;
	private String altAllele;
	private double rawScore;
	private double score;
	// AlphaGenome Variant Impact (AVI), from the genomic_score table (null when the variant has no AVI)
	private Double aviRawScore;
	private Double aviScore;

	public String getVariantKey() {
		return VariantKey.genomic(this.chromosome, position);
	}
}
