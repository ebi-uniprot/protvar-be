package uk.ac.ebi.protvar.input.format.protein;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.utils.HGVSUtils;
import uk.ac.ebi.protvar.utils.RefSeqUtils;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.utils.AminoAcid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valid format:
 * NP_xxx:p.(Arg490Ser)     regex
 * NP_xxx:p.Arg490Ser    -> AA3 + POS + AA3
 * NP_xxx:p.R490S        -> AA1 + POS + AA1
 * NP_xxx:p.Trp87Ter     -> AA3 + POS + AA3
 * NP_xxx:p.Trp78*       -> AA3 + POS + *
 * NP_xxx:p.W87*         -> AA1 + POS + AA1
 */
@Getter
public class HGVSp extends ProteinInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(HGVSp.class);
    public static final String PREFIX = RefSeqUtils.NP; // -> lookup transcript (enst)?
    public static final String SCHEME = "p.";

    public final static String THREE_LETTER_AA_PLUS_STOP_CODON = String.format("(%s|%s)", String.join("|", AminoAcid.VALID_AA3), STOP_CODON);

    public static final String ONE_LETTER_AA_SUB = "(?<ref>"+ONE_LETTER_AA + ")(?<pos>" + POS + ")(?<alt>" + ONE_LETTER_AA + ")"; // includes STOP_CODON (*)
    public static final String THREE_LETTER_AA_SUB = "(?<ref>"+THREE_LETTER_AA + ")(?<pos>" + POS + ")(?<alt>" + THREE_LETTER_AA_PLUS_STOP_CODON + ")";

    private static final String REGEX =
            "(?<rsAcc>"+PREFIX + RefSeqUtils.RS_ACC_NUM_PART + ")" + // RefSeq.NP accession
            "("+HGVSUtils.COLON + SCHEME + ")"; // :p.

    private static Pattern patternOneLetter = Pattern.compile(REGEX + ONE_LETTER_AA_SUB, Pattern.CASE_INSENSITIVE);
    private static Pattern patternThreeLetter = Pattern.compile(REGEX + THREE_LETTER_AA_SUB, Pattern.CASE_INSENSITIVE);


    String rsAcc;

    private HGVSp(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_PROT);
    }

    public static boolean maybeHGVSp(String inputStr) {
        return HGVSUtils.maybeHGVS(PREFIX, SCHEME, inputStr);
    }

    public static HGVSp parse(String inputStr) {
        HGVSp parsedInput = new HGVSp(inputStr);
        try {
            Matcher matcher = patternOneLetter.matcher(inputStr);
            if (matcher.matches()) {
                parsedInput.setParams(matcher);
            } else {
                matcher = patternThreeLetter.matcher(inputStr);
                if (matcher.matches()) {
                    parsedInput.setParams(matcher);
                } else {
                    throw new InvalidInputException("No match");
                }
            }
        } catch (Exception ex) {
            String msg = parsedInput + ": parsing error";
            parsedInput.addError(msg);
            LOGGER.error(msg, ex);
        }
        return parsedInput;
    }

    private void setParams(Matcher matcher) {
        String rsAcc = matcher.group("rsAcc"); // refseq accession
        String pos = matcher.group("pos");
        String ref = matcher.group("ref");
        String alt = matcher.group("alt");
        this.rsAcc = rsAcc;
        this.pos = Integer.parseInt(pos);
        this.ref = ref;
        this.alt = alt;
    }

    @Override
    public String toString() {
        return String.format("HGVSp [inputStr=%s]", getInputStr());
    }
}
