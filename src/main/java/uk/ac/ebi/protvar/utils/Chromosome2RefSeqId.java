package uk.ac.ebi.protvar.utils;

public enum Chromosome2RefSeqId {

	CHROMOSOME_1("1", "NC_000001.11"), CHROMOSOME_2("2", "NC_000002.12"), CHROMOSOME_3("3", "NC_000003.12"),
	CHROMOSOME_4("4", "NC_000004.12"), CHROMOSOME_5("5", "NC_000005.10"), CHROMOSOME_6("6", "NC_000006.12"),
	CHROMOSOME_7("7", "NC_000007.14"), CHROMOSOME_8("8", "NC_000008.11"), CHROMOSOME_9("9", "NC_000009.12"),
	CHROMOSOME_10("10", "NC_000010.11"), CHROMOSOME_11("11", "NC_000011.10"), CHROMOSOME_12("12", "NC_000012.12"),
	CHROMOSOME_13("13", "NC_000013.11"), CHROMOSOME_14("14", "NC_000014.9"), CHROMOSOME_15("15", "NC_000015.10"),
	CHROMOSOME_16("16", "NC_000016.10"), CHROMOSOME_17("17", "NC_000017.11"), CHROMOSOME_18("18", "NC_000018.10"),
	CHROMOSOME_19("19", "NC_000019.10"), CHROMOSOME_20("20", "NC_000020.11"), CHROMOSOME_21("21", "NC_000021.9"),
	CHROMOSOME_22("22", "NC_000022.11"), CHROMOSOME_X("X", "NC_000023.11"), CHROMOSOME_Y("Y", "NC_000024.10"),
	CHROMOSOME_MT("MT", "NC_012920.1");

	Chromosome2RefSeqId(String chromosome, String refSeqId) {
		this.chromosome = chromosome;
		this.refSeqId = refSeqId;
	}

	private final String chromosome;
	private final String refSeqId;
	
	public static String getChromosome(String refSeq) {
		for (Chromosome2RefSeqId typeVal : Chromosome2RefSeqId.values()) {
			if (typeVal.refSeqId.equals(refSeq))
				return typeVal.chromosome;
		}
		return null;
	}

}
