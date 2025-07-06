package uk.ac.ebi.protvar.model.data;

import lombok.Builder;
import lombok.Getter;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.Map;

@Getter
@Builder
public class GenomeToProteinMapping implements Comparable<GenomeToProteinMapping> {
	private String chromosome;
	private Integer genomeLocation;
	private int isoformPosition;
	private String baseNucleotide;
	private String aa;
	private String codon;
	private String accession;
	private String ensg;
	private String ensp;
	private String enst;
	private String ense;
	private boolean reverseStrand;
	private boolean isValidRecord;
	private boolean isCanonical;
	private boolean isManeSelect;
	private String patchName;
	private String geneName;
	private int codonPosition;
	private String proteinName;

	// given an alternate base, return the alt amino acid

	// DNA bases - ATCG
	// RNA bases - AUCG (used in rna codon)
	private static final Map<String, String> complementMap = Map.of(
			"A", "T",
			"T", "A",
			"G", "C",
			"C", "G"
	);
	public String getAltCodon(String altBase) {
		if (reverseStrand) {
			altBase = complementMap.get(altBase);
		}
		String altCodon = replaceChar(codon, altBase.charAt(0), codonPosition);
		return altCodon;
	}

	private String replaceChar(String str, char ch, int index) {
		if (ch == 'T') ch = 'U';
		StringBuilder sb = new StringBuilder(str);
		sb.setCharAt(index - 1, ch);
		return sb.toString();
	}

	@Override
	public String toString() {
		return "[" + isoformPosition + ":" + aa + ":" + genomeLocation + "]";
	}

	public String getVariantKeyGenomic() {
		return VariantKey.genomic(this.chromosome, this.genomeLocation);
	}

	public String getVariantKeyProtein() {
		return VariantKey.protein(this.accession, this.isoformPosition);
	}

	@Override
	public int compareTo(GenomeToProteinMapping o) {
		return Boolean.compare(o.isCanonical, this.isCanonical);
	}

}
