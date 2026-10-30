package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.input.MessageCode;
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
    // Chromosome in place of the RefSeq, e.g. chr11:g.108321424A>G - build not implied,
    // so it follows the selected (or detected) assembly like other genomic inputs
    public static final String CHR_REGEX = "(?:" + VALID_CHROMOSOME + ")";

    // no anchors (^...$)
    public static final String VARDESC_REGEX = String.format("(?<pos>%s)(?<refBase>%s)>(?<altBase>%s)",
            VALID_POSITION,
            VALID_BASE,
            VALID_BASE
    );

    // Full pattern - for matchesPattern() method
    public static final String FULL_REGEX = String.format("^(?:%s|%s)%s%s$",
            REFSEQ_REGEX,
            CHR_REGEX,
            SCHEME_REGEX,
            VARDESC_REGEX
    );
    public static final String FULL_CHR_REGEX = String.format("^%s%s%s$",
            CHR_REGEX,
            SCHEME_REGEX,
            VARDESC_REGEX
    );

    private static Pattern STRUCTURE_PATTERN = Pattern.compile(STRUCTURE_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern REFSEQ_PATTERN = Pattern.compile("^" + REFSEQ_REGEX + "$", Pattern.CASE_INSENSITIVE);
    private static Pattern CHR_PATTERN = Pattern.compile("^" + CHR_REGEX + "$", Pattern.CASE_INSENSITIVE);
    private static Pattern FULL_CHR_PATTERN = Pattern.compile(FULL_CHR_REGEX, Pattern.CASE_INSENSITIVE);
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

    /**
     * Chromosome form only (e.g. chr11:g.108321424A>G), whose build is not implied
     * by the input and so takes part in build auto-detection.
     */
    public static boolean matchesChrPattern(String input) {
        return input != null && FULL_CHR_PATTERN.matcher(input).matches();
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
                        parsedInput.addMessage(MessageCode.PV321_G_REFSEQ_NOT_CHR);
                    }
                } else if (CHR_PATTERN.matcher(refseqPart).matches()) {
                    parsedInput.setChromosome(normalizeChr(refseqPart));
                    parsedInput.addMessage(MessageCode.PV323_G_CHR_FOR_REFSEQ);
                } else {
                    parsedInput.addMessage(MessageCode.PV320_G_REFSEQ_INVALID);
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
                } else if (HGVS.NON_SUBSTITUTION.matcher(vardescPart).find()) {
                    parsedInput.addMessage(MessageCode.PV310_HGVS_NOT_SUBSTITUTION);
                } else {
                    parsedInput.addMessage(MessageCode.PV322_G_CHANGE_INVALID);
                }
            } else {
                parsedInput.addMessage(MessageCode.PV309_HGVS_INVALID);
            }
        } catch (Exception ex) {
            LOGGER.error(parsedInput + ": parsing error", ex);
            parsedInput.addMessage(MessageCode.PV309_HGVS_INVALID);
        }
        return parsedInput;
    }
}
