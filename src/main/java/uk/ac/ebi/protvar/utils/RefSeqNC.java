package uk.ac.ebi.protvar.utils;

/**
 * RefSeq accession starting with NC_ prefix
 * mapping to genomic chromosome.
 */
public enum RefSeqNC {
	NC_1("NC_000001.11","1"),
	NC_2("NC_000002.12","2"),
	NC_3("NC_000003.12","3"),
	NC_4("NC_000004.12","4"),
	NC_5("NC_000005.10","5"),
	NC_6("NC_000006.12","6"),
	NC_7("NC_000007.14","7"),
	NC_8("NC_000008.11","8"),
	NC_9("NC_000009.12","9"),
	NC_10("NC_000010.11","10"),
	NC_11("NC_000011.10","11"),
	NC_12("NC_000012.12","12"),
	NC_13("NC_000013.11","13"),
	NC_14("NC_000014.9","14"),
	NC_15("NC_000015.10","15"),
	NC_16("NC_000016.10","16"),
	NC_17("NC_000017.11","17"),
	NC_18("NC_000018.10","18"),
	NC_19("NC_000019.10","19"),
	NC_20("NC_000020.11","20"),
	NC_21("NC_000021.9","21"),
	NC_22("NC_000022.11","22"),
	NC_23("NC_000023.11","X"),
	NC_24("NC_000024.10","Y"),
	NC_MT("NC_012920.1","MT");

	private final String acc;
	private final String chr;

	RefSeqNC(String acc, String chr) {
		this.acc = acc;
		this.chr = chr;
	}
	
	public static String toChr(String acc) {
		for (RefSeqNC val : RefSeqNC.values()) {
			if (val.acc.equals(acc))
				return val.chr;
		}
		return null;
	}

}
