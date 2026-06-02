package uk.ac.ebi.protvar.input.parser.genomic;

import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.GenomicInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supported internal genomic formats; for (strict) VCF and Gnomad format, see corresponding parsers.
 *     Format	Structure	        Example
 *     Space-separated:
 *     1	    CHR POS	            chr1 12345
 *     2	    CHR POS REF	        chr1 12345 A
 *     3	    CHR POS REF ALT     chr1 12345 A T
 *     4	    CHR POS REF/ALT	    chr1 12345 A/T
 *     5	    CHR POS REF>ALT	    chr1 12345 A>T
 *     Colon-separated:
 *     6	    CHR:POS	            chr1:12345
 *     7	    CHR:POS:REF	        chr1:12345:A
 *     8	    CHR:POS:REF:ALT	    chr1:12345:A:T
 */
public class GenomicParser extends VariantParser {
    // Structural patterns - quick format check
    public static final Pattern SPACE_STRUCTURE = Pattern.compile("^\\S+\\s+\\S+(?:\\s+\\S+(?:\\s+\\S+|/\\S+|>\\S+)?)?$");
    public static final Pattern COLON_STRUCTURE = Pattern.compile("^\\S+:\\S+(?::\\S+(?::\\S+)?)?$");

    // Full validation patterns
    // Space-separated: CHR POS [REF [ALT|/ALT|>ALT]]
    public static final String SPACE_REGEX = String.format(
            "^(%s)\\s+(%s)(?:\\s+(%s)(?:\\s+(%s)|/(%s)|>(%s))?)?$",
            VALID_CHROMOSOME,  // Group 1: CHR
            VALID_POSITION,    // Group 2: POS
            VALID_BASE,        // Group 3: REF
            VALID_BASE,        // Group 4: ALT (space)
            VALID_BASE,        // Group 5: ALT (/)
            VALID_BASE         // Group 6: ALT (>)
    );

    // Colon-separated pattern: CHR:POS[:REF[:ALT]]
    public static final String COLON_REGEX = String.format(
            "^(%s):(%s)(?::(%s)(?::(%s))?)?$",
            VALID_CHROMOSOME,  // Group 1: CHR
            VALID_POSITION,    // Group 2: POS
            VALID_BASE,        // Group 3: REF
            VALID_BASE         // Group 4: ALT
    );

    public static final Pattern SPACE_PATTERN = Pattern.compile(SPACE_REGEX, Pattern.CASE_INSENSITIVE);
    public static final Pattern COLON_PATTERN = Pattern.compile(COLON_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Quick structural check - doesn't validate content, only format structure
     */
    public static boolean matchesStructure(String inputStr) {
        return SPACE_STRUCTURE.matcher(inputStr).matches() ||
                COLON_STRUCTURE.matcher(inputStr).matches();
    }

    /**
     * Full pattern validation including content validation
     */
    public static boolean matchesPattern(String inputStr) {
        return SPACE_PATTERN.matcher(inputStr).matches() ||
                COLON_PATTERN.matcher(inputStr).matches();
    }

    public static GenomicInput parse(String inputStr) {
        try {
            // Try space-separated format first
            Matcher spaceMatcher = SPACE_PATTERN.matcher(inputStr);
            if (spaceMatcher.matches()) {
                GenomicInput parsedInput = new GenomicInput(inputStr);
                parsedInput.setChromosome(normalizeChr(spaceMatcher.group(1)));
                parsedInput.setPosition(Integer.parseInt(spaceMatcher.group(2)));
                parsedInput.setRefBase(normalizeBase(spaceMatcher.group(3)));
                String alt = null;

                if (spaceMatcher.group(4) != null) {
                    alt = normalizeBase(spaceMatcher.group(4));
                } else if (spaceMatcher.group(5) != null) {
                    alt = normalizeBase(spaceMatcher.group(5));
                } else if (spaceMatcher.group(6) != null) {
                    alt = normalizeBase(spaceMatcher.group(6));
                }
                parsedInput.setAltBase(alt);
                return parsedInput;
            }

            // Try colon-separated format
            Matcher colonMatcher = COLON_PATTERN.matcher(inputStr);
            if (colonMatcher.matches()) {
                GenomicInput parsedInput = new GenomicInput(inputStr);
                parsedInput.setChromosome(normalizeChr(colonMatcher.group(1)));
                parsedInput.setPosition(Integer.parseInt(colonMatcher.group(2)));
                parsedInput.setRefBase(normalizeBase(colonMatcher.group(3)));
                parsedInput.setAltBase(normalizeBase(colonMatcher.group(4)));
                return parsedInput;
            }
        } catch (NumberFormatException e) {
            // Position parsing failed, return invalid
            return GenomicInput.invalid(inputStr);
        }

        return GenomicInput.invalid(inputStr); // No match
    }
}
