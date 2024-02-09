package uk.ac.ebi.protvar.input.format.protein;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.utils.HGVS;
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
@Setter
public class HGVSp extends ProteinInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(HGVSp.class);

    // Protein, Associated with an NM_ or NC_ accession e.g. NP_001138917.1
    public static final String PREFIX = "NP_"; // -> lookup transcript (enst)?
    public static final String SCHEME = "p.";

    public final static String THREE_LETTER_AA_PLUS_STOP_CODON = String.format("(%s|%s)", String.join("|", AminoAcid.VALID_AA3), STOP_CODON);

    public static final String ONE_LETTER_AA_SUB = "(?<ref>"+ONE_LETTER_AA + ")(?<pos>" + POS + ")(?<alt>" + ONE_LETTER_AA + ")"; // includes STOP_CODON (*)
    public static final String THREE_LETTER_AA_SUB = "(?<ref>"+THREE_LETTER_AA + ")(?<pos>" + POS + ")(?<alt>" + THREE_LETTER_AA_PLUS_STOP_CODON + ")";

    private static final String REGEX =
            "(?<rsAcc>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")" + // RefSeq.NP accession
                    "("+ HGVS.COLON + SCHEME + ")"; // :p.

    private static final String REF_SEQ =
            "(?<rsAcc>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")"; // RefSeq.NP accession

    private static final String VAR_DESC_1 = SCHEME + ONE_LETTER_AA_SUB;
    private static final String VAR_DESC_2 = SCHEME + THREE_LETTER_AA_SUB;

    private static Pattern p1 = Pattern.compile(REF_SEQ, Pattern.CASE_INSENSITIVE);
    private static Pattern p2 = Pattern.compile(VAR_DESC_1, Pattern.CASE_INSENSITIVE);
    private static Pattern p3 = Pattern.compile(VAR_DESC_2, Pattern.CASE_INSENSITIVE);



    String rsAcc;

    private HGVSp(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_PROT);
    }


    // Pre-check (level 1)
    // Pattern: NP_?:p.?
    public static boolean preCheck(String inputStr) {
        return HGVS.preCheck(PREFIX, SCHEME, inputStr);
    }

    public static boolean startsWithPrefix(String inputStr) {
        return HGVS.startsWithPrefix(PREFIX, inputStr);
    }

    public static HGVSp parse(String inputStr) {
        HGVSp parsedInput = new HGVSp(inputStr);
        try {
            String[] params = inputStr.split(HGVS.COLON);
            if (params.length == 2) {
                String refSeqPart = params[0];
                String variantDescPart = params[1];

                Matcher m1 = p1.matcher(refSeqPart);
                if (m1.matches()) {
                    String rsAcc = m1.group("rsAcc");
                    parsedInput.setRsAcc(rsAcc);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_INVALID_REFSEQ);
                }

                Matcher m2 = p2.matcher(variantDescPart);
                if (m2.matches()) { // 1 letter AA
                    parsedInput.setParams(m2);

                } else {

                    Matcher m3 = p3.matcher(variantDescPart);
                    if (m3.matches()) { // 3 letter AA
                        parsedInput.setParams(m3);
                    } else {
                        parsedInput.addError(ErrorConstants.HGVS_INVALID_VAR_DESC_P);

                        if (!variantDescPart.startsWith(SCHEME)) {
                            parsedInput.addError(ErrorConstants.HGVS_REFSEQ_P_SCHEME_MISMATCH);
                        }
                    }

                }
            } else {
                throw new InvalidInputException("No match");
            }
        } catch (Exception ex) {
            LOGGER.error(parsedInput + ": parsing error", ex);
            parsedInput.addError(ErrorConstants.HGVS_GENERIC_ERROR);
        }
        return parsedInput;
    }

    private void setParams(Matcher matcher) {
        String pos = matcher.group("pos");
        String ref = matcher.group("ref");
        String alt = matcher.group("alt");
        this.pos = Integer.parseInt(pos);
        this.ref = ref;
        this.alt = alt;
    }

    @Override
    public String toString() {
        return String.format("HGVSp [inputStr=%s]", getInputStr());
    }
}
