package uk.ac.ebi.protvar.input.format.genomic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.utils.HGVSUtils;
import uk.ac.ebi.protvar.utils.RefSeqUtils;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.utils.RefSeqNC;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HGVSg extends GenomicInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(HGVSg.class);
    public static final String PREFIX = RefSeqUtils.NC; // -> converts to chr

    public static final String SCHEME = "g.";

    public static final String REGEX = "(?<acc>"+PREFIX + RefSeqUtils.RS_ACC_NUM_PART + ")" + // RefSeq.NC accession
            "("+HGVSUtils.COLON + SCHEME + ")" + // :g.
            "(?<pos>"+GenomicInput.POS + ")" +
            "(?<sub>"+GenomicInput.BASE_SUB + ")"; // (A|T|C|G)>(A|T|C|G)

    private static Pattern pattern = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);
    private HGVSg(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_GEN);
    }

    public static boolean maybeHGVSg(String inputStr) {
        return HGVSUtils.maybeHGVS(PREFIX, SCHEME, inputStr);
    }

    public static HGVSg parse(String inputStr) {
        HGVSg parsedInput = new HGVSg(inputStr);
        try {
            Matcher matcher = pattern.matcher(inputStr);
            if (matcher.matches()) {

                String rsAcc = matcher.group("acc"); // refseq accession
                String chr = RefSeqNC.toChr(rsAcc);
                String pos = matcher.group("pos");
                String sub = matcher.group("sub");
                String[] bases = sub.split(HGVSUtils.SUB_SIGN);
                String ref = bases[0];
                String alt = bases[1];

                parsedInput.setChr(chr);
                parsedInput.setPos(Integer.parseInt(pos));
                parsedInput.setRef(ref);
                parsedInput.setAlt(alt);
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

    public static Integer extractLocation(String inputStr) {
        Matcher matcher = pattern.matcher(inputStr);
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
