package uk.ac.ebi.protvar.utils;

import java.util.HashMap;
import java.util.Map;

public class ReverseCompliment {

	private static final Map<String, String> COMPLEMENT_MAP = new HashMap<>() {{
		put("A", "T");
		put("T", "A");
		put("G", "C");
		put("C", "G");
	}};

	public static String getCompliment(String baseNucleotide) {
		return COMPLEMENT_MAP.get(baseNucleotide);
	}
}
