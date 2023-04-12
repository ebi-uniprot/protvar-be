package uk.ac.ebi.protvar.model.data;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GenomeToProteinMapping implements Comparable<GenomeToProteinMapping> {
	private String chromosome;
	private Long genomeLocation;
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

	@Override
	public String toString() {
		return "[" + isoformPosition + ":" + aa + ":" + genomeLocation + "]";
	}

	public String getGroupBy() {
		return this.chromosome + "-" + this.getGenomeLocation();
	}

	public String getGroupByProteinAccAndPos() {
		return this.accession + "-" + this.isoformPosition;
	}

	@Override
	public int compareTo(GenomeToProteinMapping o) {
		return Boolean.compare(o.isCanonical, this.isCanonical);
	}

}
