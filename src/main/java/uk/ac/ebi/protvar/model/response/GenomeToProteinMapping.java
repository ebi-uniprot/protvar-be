package uk.ac.ebi.protvar.model.response;

import lombok.Getter;

@Getter
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
	private String patchName;
	private String geneName;
	private int codonPosition;
	private String proteinName;

	public GenomeToProteinMapping(String chromosome, Long genomeLocation, int proteinLocation, String baseNucleotide,
			String aa, String codon, String accession, String ensg, String ensp, String enst, String ense,
			boolean reverseStrand, boolean isValidRecord, String patchName, String geneName, int codonPosition,
			boolean isCanonical, String proteinName) {
		super();
		this.chromosome = chromosome;
		this.genomeLocation = genomeLocation;
		this.isoformPosition = proteinLocation;
		this.baseNucleotide = baseNucleotide;
		this.aa = aa;
		this.codon = codon;
		this.accession = accession;
		this.ensg = ensg;
		this.ensp = ensp;
		this.enst = enst;
		this.ense = ense;
		this.reverseStrand = reverseStrand;
		this.isValidRecord = isValidRecord;
		this.patchName = patchName;
		this.geneName = geneName;
		this.codonPosition = codonPosition;
		this.isCanonical = isCanonical;
		this.proteinName = proteinName;
	}

	@Override
	public String toString() {
		return "[" + isoformPosition + ":" + aa + ":" + genomeLocation + "]";
	}

	public String getGroupBy() {
		return this.chromosome + "-" + this.getGenomeLocation();
	}

	@Override
	public int compareTo(GenomeToProteinMapping o) {
		return Boolean.compare(o.isCanonical, this.isCanonical);
	}

}
