package uk.ac.ebi.protvar.input.parser.genomic;

import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.ac.ebi.protvar.utils.RegexUtils.SPACES_OR_SLASH_OR_GREATER;

public class GenomicInputParser extends InputParser {
    // TODO - in protvar-import
    // make MT the default Mitochondrion
    // [X] in be code
    // [X] in protvar-import
    // [X] in db tbl
    //    [X] change genomic_protein_mapping.chromosome column from VARCHAR(15) to VARCHAR(2)
    //        SELECT DISTINCT chromosome FROM genomic_protein_mapping;
    //        UPDATE genomic_protein_mapping SET chromosome='MT' WHERE chromosome='Mitochondrion';
    //        alter table genomic_protein_mapping alter chromosome type VARCHAR(2);
    //        tbl size 64.2GB -> XGB
    // [ ] in code, map any other Mitochondrion string to MT

    /**
     * numeric chromosome 1-22 with leading zeroes accepted
     * Leading zeroes is removed during parsing (convertChr)
     */
    public static final String CHR_1_22 = "0*([1-9]|1[0-9]|2[0-2])";

    /**
     * chromosome with 'chr' prefix incl. all numeric (without leading zeroes), X, Y and M, MT
     * e.g. chr1, chrX, etc
     * Prefix is removed during parsing (convertChr)
     * M is converted to MT after prefix removed
     */
    public static final String CHR_chr = "chr([1-9]|1[0-9]|2[0-2]|X|Y|M|MT)";

    /**
     * X and Y chromosome
     */
    public static final String CHR_XY = "(X|Y)";

    /**
     * All acceptable M chromosome names.
     * Converted to MT during parsing (convertChr)
     */
    public static final String CHR_MT = "(M|MT|mit|mtDNA|mitochondria|mitochondrion)";

    public static final String MT = "MT";
    public static final String CHR = String.format("(%s|%s|%s|%s)", CHR_1_22, CHR_chr, CHR_XY, CHR_MT);

    public static final List<String> VALID_ALLELES = List.of("A", "T", "C", "G");
    public static final String BASE_SUB = BASE +  HGVS.SUB_SIGN + BASE;



    // Internal genomic formats
    // For (strict) VCF and Gnomad, see corresponding classes
    // regex = "chr pos( ref( alt)?)?";
    // format 1 - chr pos only
    // format 2 - chr pos ref
    // format 3 - chr pos ref alt (ref>alt|ref/alt)

    // note: nested optional group ( ref ( alt)?)?  group 1         2     3    4    5 6         7
    static final Pattern PATTERN = Pattern.compile("^(\\S+)\\s+(\\S+)(\\s+(\\S+)((\\s+|/|>)(\\S+))?)?$", Pattern.CASE_INSENSITIVE);

    public static boolean matchesPattern(String inputStr) {
        return PATTERN.matcher(inputStr).find();
    }

    public static boolean startsWithChromo(String input) {
        if (input != null && !input.isEmpty()) {
            String[] params = input.split("\\s+|-"); // space (for VCF and internal genomic format)
            // or dash (for Gnomad)
            if (params.length > 0) {
                return validChr(params[0]);
            }
        }
        return false;
    }
    // valid functions of individual component of input string

    public static boolean validChr(String chr) {
        return RegexUtils.matchIgnoreCase(CHR, Commons.trim(chr));
    }

    public static boolean validPos(String pos) {
        return Pattern.matches(POS, Commons.trim(pos));
    }

    public static boolean validBase(String base) {
        return RegexUtils.matchIgnoreCase(BASE, Commons.trim(base));
    }


    public static GenomicInput parse(String inputStr) {
        GenomicInput parsedInput = new GenomicInput(inputStr);
        try {
            Matcher matcher = PATTERN.matcher(inputStr);
            if (matcher.matches()) {
                String chr = matcher.group(1);
                String pos = matcher.group(2);
                String sub = matcher.group(3);

                parseChr(chr, parsedInput);
                parsePos(pos, parsedInput);
                if (sub != null) {
                    String[] bases = sub.trim().split(SPACES_OR_SLASH_OR_GREATER);
                    if (bases.length > 0) {
                        parseRef(bases[0], parsedInput);
                    }
                    if (bases.length > 1) {
                        parseAlt(bases[1], parsedInput);
                    }
                }
            } else {
                throw new InvalidInputException("No match found.");
            }
        } catch (Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_GENOMIC_INPUT);
            LOGGER.error(parsedInput + ": parsing error", ex);
        }
        return parsedInput;
    }

    public static String convertChr(String chr) {
        chr = Commons.trim(chr).toUpperCase();
        if (RegexUtils.matchIgnoreCase(CHR_1_22, chr))
            return chr.replaceFirst("^0+(?!$)", ""); // remove any leading zeros

        if (RegexUtils.matchIgnoreCase(CHR_chr, chr)) {
            chr = chr.substring(3); // remove 'chr' prefix
            if (chr.equalsIgnoreCase("M"))
                return MT;
            return chr;
        }
        if (RegexUtils.matchIgnoreCase(CHR_MT, chr))
            return MT;
        // for any other chr
        if (validChr(chr))
            return chr;
        return Constants.NA;
    }

    public static String convertId(String id) {
        id = id.trim();
        if (id.equals("."))
            return null;
        return id;
    }

    public static void parseChr(String chr, GenomicInput input) {
        if (RegexUtils.matchIgnoreCase(CHR, chr))
            input.setChr(convertChr(chr));
        else
            input.addError(ErrorConstants.INVALID_CHR);
    }

    public static void parsePos(String pos, GenomicInput input) {
        if (RegexUtils.matchIgnoreCase(POS, pos)) {
            try {
                input.setPos(Integer.parseInt(pos));
            } catch (NumberFormatException ex) {
                input.addError(ErrorConstants.INVALID_POS);
            }
        }
        else
            input.addError(ErrorConstants.INVALID_POS);
    }

    public static void parseId(String id, GenomicInput input) {
        input.setId(convertId(id));
    }

    public static void parseRef(String ref, GenomicInput input) {
        if (RegexUtils.matchIgnoreCase(BASE, ref))
            input.setRef(ref.toUpperCase());
        else
            input.addError(ErrorConstants.INVALID_REF);
    }

    public static void parseAlt(String alt, GenomicInput input) {
        if (RegexUtils.matchIgnoreCase(BASE, alt))
            input.setAlt(alt.toUpperCase());
        else
            input.addError(ErrorConstants.INVALID_ALT);
    }

    public static Integer convertPosition(String sPosition) {
        int position = -1;
        try {
            position = Integer.parseInt(sPosition.trim());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        if (position <= 0)
            position = -1;
        return position;
    }
}
