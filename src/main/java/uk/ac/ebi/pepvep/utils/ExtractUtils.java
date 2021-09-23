package uk.ac.ebi.pepvep.utils;

public class ExtractUtils {

	private static final String LOCATION_SPLITTER = "_";
	private static final String REGEX_NUMBER = "\\D+";
	private static final String REGEX_DIGIT = "[0-9]";

	public static Long extractLocation(String genomicLocation) {
		if (genomicLocation == null)
			return null;
		String[] loc = genomicLocation.split(Constants.GENOMIC_SYMBOL);
		if (loc.length >= 2) {
			String startLoc = loc[1].split(LOCATION_SPLITTER)[0];
			return Long.parseLong(startLoc.replaceAll(REGEX_NUMBER, ""));
		}
		return null;
	}

	public static String extractAllele(String genomicLocation, String codon) {
		String allele = extractAllele(genomicLocation);
		if (allele == null && codon != null) {
			allele = codon.replace(Constants.SLASH, Constants.VARIANT_SEPARATOR);
		}
		return allele;
	}

	private static String extractAllele(String genomicLocation) {
		if (!containsAllele(genomicLocation))
			return null;
		String[] loc = genomicLocation.split(Constants.GENOMIC_SYMBOL);
		if (loc.length >= 2) {
			return loc[1].replaceAll(REGEX_DIGIT, "");
		}
		return null;
	}

	private static boolean containsAllele(String genomicLocation) {
		return genomicLocation != null && genomicLocation.contains(Constants.VARIANT_SEPARATOR);
	}
}
