package uk.ac.ebi.protvar.input.parser.genomic;

import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.GenomicInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GnomadParser extends VariantParser {
    // Structural pattern - quick format check: ?-?-?-?
    public static final Pattern GNOMAD_STRUCTURE = Pattern.compile("^\\S+-\\S+-\\S+-\\S+$");

    // Full validation pattern
    // GnomAD format: CHR-POS-REF-ALT (all components required, dash-separated)
    public static final String GNOMAD_REGEX = String.format(
            "^(%s)-(%s)-(%s)-(%s)$",
            VALID_CHROMOSOME,  // Group 1: CHR (required)
            VALID_POSITION,    // Group 2: POS (required)
            VALID_BASE,        // Group 3: REF (required)
            VALID_BASE         // Group 4: ALT (required)
    );

    public static final Pattern GNOMAD_PATTERN = Pattern.compile(GNOMAD_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Quick structural check - doesn't validate content, only format structure
     */
    public static boolean matchesStructure(String inputStr) {
        return GNOMAD_STRUCTURE.matcher(inputStr).matches();
    }

    /**
     * Full pattern validation including content validation
     */
    public static boolean matchesPattern(String inputStr) {
        return GNOMAD_PATTERN.matcher(inputStr).matches();
    }

    /**
     * Parses a GnomAD format variant string: CHR-POS-REF-ALT
     * All components are required.
     *
     * @param inputStr Input string in format CHR-POS-REF-ALT
     * @return GenomicInput object with parsed and normalized data, or invalid if parsing fails
     */
    public static GenomicInput parse(String inputStr) {
        try {
            Matcher matcher = GNOMAD_PATTERN.matcher(inputStr);
            if (matcher.matches()) {
                GenomicInput parsedInput = new GenomicInput(inputStr);
                parsedInput.setFormat(VariantFormat.GNOMAD);
                parsedInput.setChromosome(normalizeChr(matcher.group(1)));
                parsedInput.setPosition(Integer.parseInt(matcher.group(2)));
                parsedInput.setRefBase(normalizeBase(matcher.group(3)));
                parsedInput.setAltBase(normalizeBase(matcher.group(4)));
                return parsedInput;
            }
        } catch (NumberFormatException e) {
            // Position parsing failed, return invalid
            return GenomicInput.invalid(inputStr);
        }

        return GenomicInput.invalid(inputStr); // No match
    }
}
