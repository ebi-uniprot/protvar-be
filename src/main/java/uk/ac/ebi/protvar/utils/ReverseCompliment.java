package uk.ac.ebi.protvar.utils;

import java.util.HashMap;
import java.util.Map;

public class ReverseCompliment {

	private static Map<String, String> COMPLPIMENT_MAP = null;

	private static void init() {
		COMPLPIMENT_MAP = new HashMap<>();
		COMPLPIMENT_MAP.put("A", "T");
		COMPLPIMENT_MAP.put("T", "A");
		COMPLPIMENT_MAP.put("G", "C");
		COMPLPIMENT_MAP.put("C", "G");
	}

	public static String getCompliment(String baseNucleotide) {
		if (COMPLPIMENT_MAP == null)
			init();
		return COMPLPIMENT_MAP.get(baseNucleotide);
	}

}
