package uk.ac.ebi.protvar.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

//Combine three nucleotide (from gene) makes one codon
//Left hand codon right hand amino acid in 3 letters
public class Codon2AminoAcid {
  private static final Logger logger = LoggerFactory.getLogger(Codon2AminoAcid.class);
	private static final String CODON_MAPPING_FILE = "codon.txt";
	private static Map<String, String> codonAminoAcidMap = null;

	private static void initCodonMap() {
		codonAminoAcidMap = new HashMap<>();
		InputStream inputFS = Codon2AminoAcid.class.getClassLoader().getResourceAsStream(CODON_MAPPING_FILE);
		assert inputFS != null;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputFS, StandardCharsets.UTF_8))) {
      br.lines()
        .filter(Commons::notNullNotEmpty)
        .map(Commons::trim)
        .filter(line -> !line.startsWith("#"))
        .forEach(line -> {
          String[] p = line.split("=");
          codonAminoAcidMap.put(p[0], p[1]);
        });
    } catch (IOException e) {
      logger.error("Failed to close file", e);
    }
  }

	public static String getAminoAcid(String codon) {
		if (codonAminoAcidMap == null)
			initCodonMap();
		return codonAminoAcidMap.get(codon);
	}

}
