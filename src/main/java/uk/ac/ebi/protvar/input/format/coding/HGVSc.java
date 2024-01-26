package uk.ac.ebi.protvar.input.format.coding;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.utils.HGVSUtils;
import uk.ac.ebi.protvar.utils.RefSeqUtils;
import uk.ac.ebi.protvar.input.type.CodingInput;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.utils.RegexUtils;

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
    public static final String POS = GenomicInput.POS;
    public static final String GENE = "[A-Z][A-Z0-9]+"; // MAIN CRITERIA: must only contain uppercase letters and numbers, start with a letter

    private static final String REGEX =
            "(?<rsAcc>"+PREFIX + RefSeqUtils.RS_ACC_NUM_PART + ")" + // RefSeq.NM accession
                    // OPTIONALLY, gene name
                    "(("+ RegexUtils.SPACES +")?\\((?<gene>"+GENE+")\\))?" +
                    "("+ HGVSUtils.COLON + SCHEME + ")" + // :c.
                    "(?<pos>" + POS + ")" +
                    "(?<ref>" + GenomicInput.BASE + ")" +
                    "(" + HGVSUtils.SUB_SIGN + ")" +
                    "(?<alt>" + GenomicInput.BASE + ")" +
                    // OPTIONALLY, with or without space followed by protein substitution
                    "(("+ RegexUtils.SPACES +")?\\(p.(?<protRef>"+ ProteinInput.THREE_LETTER_AA + ")(?<protPos>" + POS + ")(?<protAlt>" + ProteinInput.THREE_LETTER_AA+")\\))?";

    private static Pattern pattern = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);


    String rsAcc; // refseq id
    Integer pos; // Coding DNA position
    String ref;
    String alt;
    // optional
    String gene;
    String protRef;
    String protAlt;
    Integer protPos;

    // derived
    String derivedUniprotAcc;
    Integer derivedProtPos;
    Integer derivedCodonPos;

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
                String rsAcc = matcher.group("rsAcc"); // refseq accession
                String pos = matcher.group("pos");
                String ref = matcher.group("ref");
                String alt = matcher.group("alt");
                parsedInput.setRsAcc(rsAcc);
                parsedInput.setPos(Integer.parseInt(pos));
                parsedInput.setRef(ref);
                parsedInput.setAlt(alt);
                // optional fields
                String gene = matcher.group("gene");
                String protRef = matcher.group("protRef");
                String protAlt = matcher.group("protAlt");
                String protPos = matcher.group("protPos");

                parsedInput.setGene(gene);
                parsedInput.setProtRef(protRef);
                parsedInput.setProtAlt(protAlt);
                parsedInput.setProtPos(protPos == null ? null : Integer.parseInt(protPos));

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
