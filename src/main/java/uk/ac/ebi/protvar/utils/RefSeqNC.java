package uk.ac.ebi.protvar.utils;

/**
 * NCBI Genome assembly GRCh38.p14
 * https://www.ncbi.nlm.nih.gov/datasets/genome/GCF_000001405.40/
 *
 * Revision history
 * GenBank RefSeq Name Level Date
 * GCA_000001405.29	GCF_000001405.40^	GRCh38.p14	Chromosome	Feb 3, 2022   -- LAST GRCh38
 * GCA_000001405.15	GCF_000001405.26	GRCh38	Chromosome	Dec 17, 2013	  -- FIRST GRCh38
 * GCA_000001405.14	GCF_000001405.25	GRCh37.p13	Chromosome	Jun 28, 2013  -- LAST GRCh37
 * GCA_000001405.1	GCF_000001405.13	GRCh37	Chromosome	Feb 27, 2009	  -- FIRST GRCh37
 *
 * Note:
 * GRCh38: no change between the last (p14) and the first version acc-chr mapping.
 * GRCh37: has mapping for the last version only.
 *
 * RefSeq accession starting with NC_ prefix
 * mapping to genomic chromosome.
 */
public enum RefSeqNC {
	NC_1("NC_000001.11", "NC_000001.10", "1"),
	NC_2("NC_000002.12", "NC_000002.11", "2"),
	NC_3("NC_000003.12", "NC_000003.11", "3"),
	NC_4("NC_000004.12", "NC_000004.11", "4"),
	NC_5("NC_000005.10", "NC_000005.9", "5"),
	NC_6("NC_000006.12", "NC_000006.11", "6"),
	NC_7("NC_000007.14", "NC_000007.13", "7"),
	NC_8("NC_000008.11", "NC_000008.10", "8"),
	NC_9("NC_000009.12", "NC_000009.11", "9"),
	NC_10("NC_000010.11", "NC_000010.10", "10"),
	NC_11("NC_000011.10", "NC_000011.9", "11"),
	NC_12("NC_000012.12", "NC_000012.11", "12"),
	NC_13("NC_000013.11", "NC_000013.10", "13"),
	NC_14("NC_000014.9", "NC_000014.8", "14"),
	NC_15("NC_000015.10", "NC_000015.9", "15"),
	NC_16("NC_000016.10", "NC_000016.9", "16"),
	NC_17("NC_000017.11", "NC_000017.10", "17"),
	NC_18("NC_000018.10", "NC_000018.9", "18"),
	NC_19("NC_000019.10", "NC_000019.9", "19"),
	NC_20("NC_000020.11", "NC_000020.10", "20"),
	NC_21("NC_000021.9", "NC_000021.8", "21"),
	NC_22("NC_000022.11", "NC_000022.10", "22"),
	NC_23("NC_000023.11", "NC_000023.10", "X"),
	NC_24("NC_000024.10", "NC_000024.9", "Y"),
	NC_MT("NC_012920.1", "NC_012920.1", "MT");  // same for both builds

	private final String grch38Acc;
	private final String grch37Acc;
	private final String chr;

	RefSeqNC(String grch38Acc, String grch37Acc, String chr) {
		this.grch38Acc = grch38Acc;
		this.grch37Acc = grch37Acc;
		this.chr = chr;
	}
	
	public static String toChr(String acc) {
		for (RefSeqNC val : RefSeqNC.values()) {
			if (val.grch38Acc.equalsIgnoreCase(acc))
				return val.chr;
		}
		return null;
	}

}