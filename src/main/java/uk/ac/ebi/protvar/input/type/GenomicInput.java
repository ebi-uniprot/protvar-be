package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.genomic.VCF;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.HGVSUtils;
import uk.ac.ebi.protvar.utils.RegexUtils;
import static uk.ac.ebi.protvar.utils.RegexUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
public class GenomicInput extends UserInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenomicInput.class);

    // TODO - in protvar-import
    // make MT the default Mitochondrion
    // [X] in be code
    // [ ] in protvar-import
    // [X] in db tbl
    //    [X] change genomic_protein_mapping.chromosome column from VARCHAR(15) to VARCHAR(2)
    //        SELECT DISTINCT chromosome FROM genomic_protein_mapping;
    //        UPDATE genomic_protein_mapping SET chromosome='MT' WHERE chromosome='Mitochondrion';
    //        alter table genomic_protein_mapping alter chromosome type VARCHAR(2);
    //        tbl size 64.2GB -> XGB
    // [ ] in code, map any other Mitochondrion string to MT
    //

    public static final String CHR_1_23 = "0*(2[0-2]|1[0-9]|[1-9])";
    public static final String CHR_XY = "(X|Y)";
    public static final String CHR_MT = "(chrM|mitochondria|mitochondrion|MT|mtDNA|mit)";
    public static final String MT = "MT";
    public static final String CHR = String.format("(%s|%s|%s)", CHR_1_23, CHR_XY, CHR_MT);
    public static final String POS = "([0-9]*[1-9][0-9]*)";  // positive-only integers incl. w/ leading zeros
    public static final String BASE = "(A|T|C|G)";

    public static final List<String> VALID_ALLELES = List.of("A", "T", "C", "G");
    public static final String BASE_SUB = BASE +  HGVSUtils.SUB_SIGN + BASE;


    // Custom genomic formats
    // (Note VCF class captures strict VCF format, and Gnomad class captures Gnomad format)

    public static final String SUB = SPACES_OR_SLASH_OR_GREATER;

    // Format 1 - chr pos only
    public static final String CUSTOM_GENOMIC_CHR_POS = CHR + SPACES + POS;

    // Format 2 - chr pos & 1 base
    public static final String CUSTOM_GENOMIC_CHR_POS_BASE = CHR + SPACES + POS + SPACES + BASE;

    // Format 3 - chr pos & base sub (both ref and alt base)
    public static final String CUSTOM_GENOMIC_CHR_POS_BASE_SUB = CHR + SPACES + POS + SPACES
            + BASE + SUB + BASE;

    // ^^^ above covers (lenient) VCF w/o variant ID
    // TODO need to cover following loose VCF formats as well:
    // chr pos id
    // chr pos id ref
    // chr pos id ref alt -> should be captured by VCF class
    //                       BUT NOT ref>alt & ref/alt
    public static final String REGEX = "(?<chr>"+CHR + ")" + SPACES +
            "(?<pos>"+POS + ")" +
            // Note a valid base (A|T|C|G) may be captured as an ID in the regex below, check needed
            "(?<c1>(("+ SPACES + ")" + VCF.ID + "))?" +
            "(?<c2>(("+ SPACES + ")" + BASE + "))?" +
            "(?<c3>(("+ SUB + ")" + BASE + "))?";

    // Following does not work because group name duplicate.
    // "(SPACES + (?<ref>BASE
    //              | ?<ref>BASE + SPACES + ?<alt>BASE
    //              | ?<id>VCF.ID
    //              | ?<id>VCF.ID + SPACES + ?<ref>BASE
    //              | ?<id>VCF.ID + SPACES + ?<ref>BASE + SPACES + ?<alt>BASE))?"
    // NOTHING
    // base             <- order matters as any base (A,T,C,G) will also match ID regex
    // base base
    // id
    // id base
    // id base base

    private static Pattern pattern = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);

    String chr;
    Integer pos;
    String ref;
    String alt;
    String id;

    Boolean converted;

    // output or result of input
    List<GenomeProteinMapping> mappings = new ArrayList<>();

    public GenomicInput(String userInput) {
        setType(Type.GENOMIC);
        setFormat(Format.CUSTOM_GEN); // is custom unless specialised by extending class.
        // Note GenomicInput not abstract so it may be instantiated for e.g. in the case
        // of custom genomic input.
        setInputStr(userInput);
    }

    public static boolean isValid_(String inputStr) {
        return RegexUtils.matchIgnoreCase(REGEX, inputStr);
    }

    public static boolean isValid(String inputStr) {
        inputStr = Commons.trim(inputStr);
        return RegexUtils.matchIgnoreCase(CUSTOM_GENOMIC_CHR_POS, inputStr)
                || RegexUtils.matchIgnoreCase(CUSTOM_GENOMIC_CHR_POS_BASE, inputStr)
                || RegexUtils.matchIgnoreCase(CUSTOM_GENOMIC_CHR_POS_BASE_SUB, inputStr);
    }

    public static GenomicInput parse_(String inputStr) {
        GenomicInput parsedInput = new GenomicInput(inputStr);
        try {
            Matcher matcher = pattern.matcher(inputStr);
            if (matcher.matches()) {
                String chr = matcher.group("chr");
                String pos = matcher.group("pos");
                String c1 = matcher.group("c1");
                String c2 = matcher.group("c2");
                String c3 = matcher.group("c3");

                parsedInput.setChr(convertChromosome(chr));
                parsedInput.setPos(convertPosition(pos));

                if (c1 != null && validBase(c1)) { // no ID
                    parsedInput.setId(Constants.NA);
                    parsedInput.setRef(c1 != null ? c1.toUpperCase() : null);
                    parsedInput.setAlt(c2 != null ? c2.toUpperCase() : null);
                } else { // consider c1 is ID
                    parsedInput.setId(VCF.convertId(c1));
                    parsedInput.setRef(c2 != null ? c2.toUpperCase() : null);
                    parsedInput.setAlt(c3 != null ? c3.toUpperCase() : null);
                }

            } else {
                throw new InvalidInputException("No match");
            }
        } catch (Exception ex) {
            String msg = parsedInput + ": parsing error";
            parsedInput.addError(msg);
            LOGGER.error(msg, ex);
        }
        return parsedInput;
    }

    public static GenomicInput parse(String inputStr) {
        // pre-condition: isValid
        GenomicInput parsedInput = new GenomicInput(inputStr);
        String[] params = inputStr.split(SPACES_OR_SLASH_OR_GREATER);

        if (params.length <= 1) {
            String msg = parsedInput + ": parsing error";
            parsedInput.addError(msg);
        }
        if (params.length > 1) {
            parsedInput.setChr(convertChromosome(params[0]));
            parsedInput.setPos(convertPosition(params[1]));
        }

        if (params.length > 2)
            parsedInput.setRef(params[2].toUpperCase());

        if (params.length > 3)
            parsedInput.setAlt(params[3].toUpperCase());

        if (parsedInput.getRef() != null && parsedInput.getRef().equals(parsedInput.getAlt())) {
            parsedInput.addWarning("Ref and alt base are the same");
        }
        parsedInput.setId(Constants.NA);
        return parsedInput;
    }


    public static UserInput invalidInput(String userInput){
        GenomicInput invalid = new GenomicInput(userInput);
        invalid.addError("Error parsing user input");
        return invalid;
    }

    /**
     * Default check - uses SPACE as delimiter.
     * @param input
     * @return
     */
    public static boolean startsWithChromo(String input) {
        return GenomicInput.startsWithChromo(input, SPACES);
    }

    public static boolean startsWithChromo(String input, String sep) {
        if (input != null && !input.isEmpty()) {
            String[] params = input.split(sep);
            if (params.length > 0) {
                String p1 = params[0].toUpperCase();
                return validChr(p1);
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

    public static String convertChromosome(String chr) {
        chr = Commons.trim(chr)
                .toUpperCase()
                .replaceFirst("^0+(?!$)", ""); // remove any leading zeros
        if (RegexUtils.matchIgnoreCase(CHR_MT, chr))
            return MT;
        // for any other chr
        if (validChr(chr))
            return chr;
        return Constants.NA;
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

    @Override
    public String toString() {
        return String.format("GenomicInput [inputStr=%s, chr=%s, pos=%s, ref=%s, alt=%s]",
                getInputStr(), chr, pos, ref, alt);
    }

    public String groupByChrAndPos() {
        return this.chr + "-" + this.pos;
    }

    public String getGroupBy() {
        return groupByChrAndPos();
    }

    // Overriding equals() to compare two Genomic objects
    @Override
    public boolean equals(Object o) {

        // If the object is compared with itself then return true
        if (o == this) {
            return true;
        }

        /* Check if o is an instance of Genomic or not
          "null instanceof [type]" also returns false */
        if (!(o instanceof GenomicInput)) {
            return false;
        }

        // typecast o to Genomic so that we can compare data members
        GenomicInput g = (GenomicInput) o;

        return this.chr.equals(g.chr)
                && this.pos == g.pos
                && this.ref.equals(g.ref)
                && this.alt.equals(g.alt)
                && this.id.equals(g.id)
                && this.getInputStr().equals(g.getInputStr());
    }
}
