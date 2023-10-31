package uk.ac.ebi.protvar.input.format.coding;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.utils.HGVSUtils;
import uk.ac.ebi.protvar.utils.RefSeqUtils;
import uk.ac.ebi.protvar.input.type.CodingInput;
import uk.ac.ebi.protvar.input.type.GenomicInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refer to HGVS doc: https://varnomen.hgvs.org/bg-material/simple/
 * mRNA
 */
@Getter
@Setter
public class HGVSc extends CodingInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(HGVSc.class);
    public static final String PREFIX = RefSeqUtils.NM;
    public static final String SCHEME = "c.";

    public static final String REGEX = "(?<acc>"+PREFIX+ RefSeqUtils.RS_ACC_NUM_PART + ")" + // RefSeq.NM accession
            "("+ HGVSUtils.COLON + SCHEME + ")" + // :c.
            "(?<pos>"+GenomicInput.POS + ")" +
            "(?<sub>"+GenomicInput.BASE_SUB + ")"; // (A|T|C|G)>(A|T|C|G)

    private static Pattern pattern = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);

    String rsAcc;
    private HGVSc(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_CODING);
    }

    public static boolean maybeHGVSc(String inputStr) {
        return HGVSUtils.maybeHGVS(PREFIX, SCHEME, inputStr);
    }

    public static HGVSc parse(String inputStr) {
        HGVSc parsedInput = new HGVSc(inputStr);
        try {
            Matcher matcher = pattern.matcher(inputStr);
            if (matcher.matches()) {

                String rsAcc = matcher.group("acc"); // refseq accession
                String pos = matcher.group("pos");
                String sub = matcher.group("sub");
                String[] bases = sub.split(HGVSUtils.SUB_SIGN);
                String ref = bases[0];
                String alt = bases[1];

                parsedInput.setRsAcc(rsAcc);
                parsedInput.setCodingPos(Integer.parseInt(pos));
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

    @Override
    public String toString() {
        return String.format("HGVSc [inputStr=%s]", getInputStr());
    }

}
