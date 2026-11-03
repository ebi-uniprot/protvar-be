package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.VariantParser;

import java.util.regex.Pattern;

/**
 * UniProt variant ID parser: the humsavar FTId of a Swiss-Prot human missense variant, VAR_ + 6 digits, e.g. VAR_018369.
 * Parsed into a {@link ProteinInput} without accession/position; {@code Id2Gen} fills them from humsavar.
 * Case-insensitive (var_018369 is accepted, stored upper case).
 */
public class UniprotVarParser extends VariantParser {
    static final Pattern STRUCTURE_PATTERN = Pattern.compile("^VAR_\\d", Pattern.CASE_INSENSITIVE);
    // VAR_ + 6 zero-padded digits (all humsavar IDs; VAR_000005..VAR_091618 in 2026_02)
    static final Pattern FULL_PATTERN = Pattern.compile("^VAR_\\d{6}$", Pattern.CASE_INSENSITIVE);

    public static boolean matchesStructure(String input) {
        return input != null && STRUCTURE_PATTERN.matcher(input).lookingAt();
    }

    public static boolean matchesPattern(String input) {
        return input != null && FULL_PATTERN.matcher(input).matches();
    }

    public static ProteinInput parse(String inputStr) {
        ProteinInput parsedInput = new ProteinInput(inputStr);
        parsedInput.setFormat(VariantFormat.UNIPROT_VAR);
        if (!matchesPattern(inputStr)) {
            LOGGER.warn("Invalid UniProt variant ID format: {}", inputStr);
            parsedInput.addMessage(MessageCode.PV407_UNIPROT_VAR_INVALID);
        }
        return parsedInput;
    }
}
