package uk.ac.ebi.protvar.input.format.genomic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.utils.RefSeqNC;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HGVSg extends GenomicInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(HGVSg.class);


    // Genomic, Complete genomic molecule, usually reference assembly
    public static final String PREFIX = "NC_"; // -> converts to chr

    public static final String SCHEME = "g.";

    public static final String REF_SEQ = "(?<rsAcc>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")"; // RefSeq NC accession
    public static final String VAR_DESC = SCHEME + // :g.
            "(?<pos>"+GenomicInput.POS + ")" +
            "(?<sub>"+GenomicInput.BASE_SUB + ")"; // (A|T|C|G)>(A|T|C|G)

    public static final String REGEX = REF_SEQ + HGVS.COLON + VAR_DESC;

    @JsonIgnore @Getter @Setter
    Boolean grch37RSAcc;

    private static Pattern p = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern p1 = Pattern.compile(REF_SEQ, Pattern.CASE_INSENSITIVE);
    private static Pattern p2 = Pattern.compile(VAR_DESC, Pattern.CASE_INSENSITIVE);


    public HGVSg(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_GEN);
    }

    // Pre-check (level 1) - starts with prefix or contains scheme
    // Pattern: NC_?:g.?
    public static boolean preCheck(String inputStr) {
        return HGVS.preCheck(PREFIX, SCHEME, inputStr);
    }

    public static boolean startsWithPrefix(String inputStr) {
        return HGVS.startsWithPrefix(PREFIX, inputStr);
    }

    public static HGVSg parse(String inputStr) {
        // pre-condition:
        // [x] fits general HGVS format - ref seq & var desc part separated by a single colon
        // [x] starts with HGVSg prefix

        HGVSg parsedInput = new HGVSg(inputStr);
        try {
            String[] params = inputStr.split(HGVS.COLON);
            if (params.length == 2) {
                String refSeqPart = params[0];
                String variantDescPart = params[1];

                Matcher m1 = p1.matcher(refSeqPart);
                if (m1.matches()) {
                    String rsAcc = m1.group("rsAcc"); // refseq accession

                    String chr = RefSeqNC.grch38RSAcctoChr(rsAcc);
                    if (chr == null) {
                        chr = RefSeqNC.grch37RSAcctoChr(rsAcc);
                        if (chr != null) {
                            parsedInput.setGrch37RSAcc(true);
                        } else {
                            parsedInput.addError(ErrorConstants.HGVS_G_REF_SEQ_NOT_MAPPED_TO_CHR);
                        }
                    }
                    parsedInput.setChr(chr);

                } else {
                    parsedInput.addError(ErrorConstants.HGVS_INVALID_REFSEQ);
                }

                Matcher m2 = p2.matcher(variantDescPart);
                if (m2.matches()) {
                    String pos = m2.group("pos");
                    String sub = m2.group("sub");
                    String[] bases = sub.split(HGVS.SUB_SIGN);
                    String ref = bases[0];
                    String alt = bases[1];

                    parsedInput.setPos(Integer.parseInt(pos));
                    parsedInput.setRef(ref);
                    parsedInput.setAlt(alt);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_INVALID_VAR_DESC_G);

                    if (!variantDescPart.startsWith(SCHEME)) {
                        parsedInput.addError(ErrorConstants.HGVS_REFSEQ_G_SCHEME_MISMATCH);
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

    public static boolean validRefSeq(String val) {
        return RegexUtils.matchIgnoreCase(PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM, val);
    }
    public static boolean validRef(String val) {
        return RegexUtils.matchIgnoreCase(BASE, val);
    }
    public static boolean validAlt(String val) {
        return RegexUtils.matchIgnoreCase(BASE, val);
    }

    public static Integer extractLocation(String inputStr) {
        Matcher matcher = p.matcher(inputStr);
        if (matcher.matches()) {
            try {
                Integer pos = Integer.parseInt(matcher.group("pos"));
                return pos;
            } catch (Exception ex) {
                // return null
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("HGVSg [inputStr=%s]", getInputStr());
    }
}
