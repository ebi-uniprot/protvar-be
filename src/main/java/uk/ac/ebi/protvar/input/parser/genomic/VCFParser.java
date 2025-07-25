package uk.ac.ebi.protvar.input.parser.genomic;

import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict VCF format parser for the first 5 mandatory columns:
 * 1. CHROM    <- REQUIRED (chromosome)
 * 2. POS      <- REQUIRED (position)
 * 3. ID       <- REQUIRED ('.' or actual ID)
 * 4. REF      <- REQUIRED (reference base)
 * 5. ALT      <- REQUIRED (alternate base)
 *
 * Additional columns (QUAL, FILTER, INFO, etc.) are ignored.
 * DELIMITER = TAB or SPACES (typically TAB in real VCF files)
 */
public class VCFParser extends VariantParser {

    // "s" stands for space
    // "S" stands for non-space

    /** regex pattern for the first five columns of a VCF format string
     * ^(\\S+): Matches the first non-whitespace sequence at the start of the line (CHROM).
     * (\\d+): Matches one or more digits (POS).
     * (\\S+): Matches the next non-whitespace sequence (ID).
     * (\\S+): Matches the next non-whitespace sequence (REF).
     * (\\S+): Matches the next non-whitespace sequence (ALT).
     *
     * \\t: Matches a tab character. (strict VCF, needs to be a tab)
     * replaced \\t with \\s+
     * also replace \\d+ for pos with \\S+, check is done in parse
     */

    // Structural pattern - quick format check: ? ? ? ? ? [optional additional fields]
    public static final Pattern VCF_STRUCTURE = Pattern.compile("^\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+(?:\\s+.*)?$");

    // Full validation pattern
    // VCF format: CHROM POS ID REF ALT [additional columns ignored]
    public static final String VCF_REGEX = String.format(
            "^(%s)\\s+(%s)\\s+(\\S+)\\s+(%s)\\s+(%s)(?:\\s+.*)?$",
            VALID_CHROMOSOME,  // Group 1: CHROM
            VALID_POSITION,    // Group 2: POS
            // Group 3: ID (any non-whitespace)
            VALID_BASE,        // Group 4: REF
            VALID_BASE         // Group 5: ALT
    );

    public static final Pattern VCF_PATTERN = Pattern.compile(VCF_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Quick structural check - doesn't validate content, only format structure
     */
    public static boolean matchesStructure(String inputStr) {
        return VCF_STRUCTURE.matcher(inputStr).matches();
    }

    /**
     * Full pattern validation including content validation
     */
    public static boolean matchesPattern(String inputStr) {
        return VCF_PATTERN.matcher(inputStr).matches();
    }

    /**
     * Parses a VCF format variant string: CHROM POS ID REF ALT [additional fields ignored]
     * All first 5 components are required.
     *
     * @param inputStr Input string in VCF format
     * @return GenomicInput object with parsed and normalized data, or invalid if parsing fails
     */
    public static GenomicInput parse(String inputStr) {

        try {
            Matcher matcher = VCF_PATTERN.matcher(inputStr);
            if (matcher.matches()) {
                GenomicInput parsedInput = new GenomicInput(inputStr);
                parsedInput.setFormat(VariantFormat.VCF);
                parsedInput.setChromosome(normalizeChr(matcher.group(1)));
                parsedInput.setPosition(Integer.parseInt(matcher.group(2)));
                parsedInput.setId(matcher.group(3));
                parsedInput.setRefBase(normalizeBase(matcher.group(4)));
                parsedInput.setAltBase(normalizeBase(matcher.group(5)));
                return parsedInput;
            }
        } catch (NumberFormatException e) { // will not happen if matches
            // Position parsing failed, return invalid
            return GenomicInput.invalid(inputStr);
        }

        return GenomicInput.invalid(inputStr); // No match
    }
}
