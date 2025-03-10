package uk.ac.ebi.protvar.utils;

public class EnsemblIDValidator {

    // Method to validate Ensembl ID
    public static boolean isValidEnsemblID(String ensemblID) {
        // Regular expression for Ensembl ID format with valid prefixes and optional version suffix (e.g., ENSTxxxxxx, ENSExxxxx, ENSPxxxxxx, or ENSGxxxxxx, followed by optional .<version>)
        String regex = "^(ENST|ENSE|ENSP|ENSG)[0-9]{11}(.[0-9]+)?$";

        if (ensemblID == null || ensemblID.isEmpty()) {
            return false;
        }

        return ensemblID.matches(regex);
    }

}