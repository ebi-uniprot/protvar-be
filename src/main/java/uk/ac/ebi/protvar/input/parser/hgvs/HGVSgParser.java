package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.types.RefseqChr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HGVSgParser extends VariantParser {
    // Genomic, Complete genomic molecule, usually reference assembly
    public static final String SCHEME_REGEX = ":(\\s*)g\\.";

    // Structural pattern - quick check for general HGVS genomic structure: _:g._
    public static final String STRUCTURE_REGEX = String.format("^(?<refseq>[^:]+)%s(?<varDesc>.+)$",
            SCHEME_REGEX // Scheme: :(optional space)g.
    );

    // no anchors (^...$)
    public static final String REFSEQ_REGEX = "(NC_)\\d+(\\.\\d+)?"; // RefSeq part: NC_ + digits + version

    // no anchors (^...$)
    public static final String VARDESC_REGEX = String.format("(?<pos>%s)(?<refBase>%s)>(?<altBase>%s)",
            VALID_POSITION,
            VALID_BASE,
            VALID_BASE
    );

    // Full pattern - for matchesPattern() method
    public static final String FULL_REGEX = String.format("^%s%s%s$",
            REFSEQ_REGEX,
            SCHEME_REGEX,
            VARDESC_REGEX
    );

    private static Pattern STRUCTURE_PATTERN = Pattern.compile(STRUCTURE_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern REFSEQ_PATTERN = Pattern.compile("^" + REFSEQ_REGEX + "$", Pattern.CASE_INSENSITIVE);
    private static Pattern VARDESC_PATTERN = Pattern.compile("^" + VARDESC_REGEX + "$", Pattern.CASE_INSENSITIVE);
    private static Pattern FULL_PATTERN = Pattern.compile(FULL_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Quick structural check - verifies input looks like HGVS genomic format.
     * Checks for general structure: RefSeq:g.variant with relaxed spacing.
     * More permissive than full pattern validation.
     */
    public static boolean matchesStructure(String inputStr) {
        if (inputStr == null || inputStr.trim().isEmpty()) {
            return false;
        }
        return STRUCTURE_PATTERN.matcher(inputStr).lookingAt();
    }

    /**
     * Full pattern validation - validates complete HGVS genomic format.
     * Validates RefSeq format, scheme, and variant description syntax.
     */
    public static boolean matchesPattern(String input) {
        return input != null && FULL_PATTERN.matcher(input).matches();
    }

    public static GenomicInput parse(String inputStr) {
        GenomicInput parsedInput = new GenomicInput(inputStr);
        parsedInput.setFormat(VariantFormat.HGVS_GENOMIC);

        try {
            Matcher structureMatcher = STRUCTURE_PATTERN.matcher(inputStr);
            if (structureMatcher.matches()) {
                String refseqPart = structureMatcher.group("refseq");
                if (REFSEQ_PATTERN.matcher(refseqPart).matches()) {
                    parsedInput.setRefseqId(refseqPart);

                    // Map RefSeq to chromosome
                    for (RefseqChr val : RefseqChr.values()) {
                        if (val.getRefseqId38().equalsIgnoreCase(refseqPart)) {
                            parsedInput.setChromosome(val.getChr());
                            break;
                        }
                        if (val.getRefseqId37().equalsIgnoreCase(refseqPart)) {
                            parsedInput.setChromosome(val.getChr());
                            parsedInput.setRefseq37(true);
                            break;
                        }
                    }
                    if (parsedInput.getChromosome() == null) {
                        parsedInput.addError(ErrorConstants.HGVS_G_REFSEQ_NOT_MAP_TO_CHR);
                    }
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_G_REFSEQ_INVALID);
                }

                String vardescPart = structureMatcher.group("varDesc");
                Matcher vardescMatcher = VARDESC_PATTERN.matcher(vardescPart);

                if (vardescMatcher.matches()) {
                    String pos = vardescMatcher.group("pos");
                    String refBase = vardescMatcher.group("refBase");
                    String altBase = vardescMatcher.group("altBase");

                    parsedInput.setPosition(Integer.parseInt(pos));
                    parsedInput.setRefBase(refBase);
                    parsedInput.setAltBase(altBase);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_G_VARDESC_INVALID);
                }
            } else {
                parsedInput.addError("HGVS g. invalid"); // todo: ErrorConstants.HGVS_G_FORMAT_INVALID
            }
        } catch (Exception ex) {
            LOGGER.error(parsedInput + ": parsing error", ex);
            parsedInput.addError(ErrorConstants.HGVS_GENERIC_ERROR);
        }
        return parsedInput;
    }
}
