package uk.ac.ebi.pepvep.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

//Combine three nucleotide (from gene) makes one codon
//Left hand codon right hand amino acid in 3 letters
public class Codon2AminoAcid {
	private static final String CODON_MAPPING_FILE = "codon.txt";
	private static Map<String, String> codonAminoAcidMap = null;

	private static void initCodonMap() {
		codonAminoAcidMap = new HashMap<>();
		InputStream inputFS = Codon2AminoAcid.class.getClassLoader().getResourceAsStream(CODON_MAPPING_FILE);
		assert inputFS != null;
		BufferedReader br = new BufferedReader(new InputStreamReader(inputFS));
		br.lines().forEach(line -> {
			String[] p = line.split("=");
			codonAminoAcidMap.put(p[0], p[1]);
		});
	}

	public static String getAminoAcid(String codon) {
		if (codonAminoAcidMap == null)
			initCodonMap();
		return codonAminoAcidMap.get(codon);
	}

}
