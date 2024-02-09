package uk.ac.ebi.protvar.input.format.coding;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.utils.HGVS;
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

    // mRNA, Protein-coding transcripts (usually curated) e.g. NM_001145445.1
    public static final String PREFIX = "NM_";
    public static final String SCHEME = "c.";
    public static final String POS = GenomicInput.POS;
    public static final String GENE = "[A-Z][A-Z0-9]+"; // MAIN CRITERIA: must only contain uppercase letters and numbers, start with a letter

    private static final String REF_SEQ =
            "(?<rsAcc>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")" + // RefSeq NM accession
                    // OPTIONALLY, gene name
                    "(("+ RegexUtils.SPACES +")?\\((?<gene>"+GENE+")\\))?";

    private static final String VAR_DESC =
                    SCHEME + // :c.
                    "(?<pos>" + POS + ")" +
                    "(?<ref>" + GenomicInput.BASE + ")" +
                    "(" + HGVS.SUB_SIGN + ")" +
                    "(?<alt>" + GenomicInput.BASE + ")" +
                    // OPTIONALLY, with or without space followed by protein substitution
                    "(("+ RegexUtils.SPACES +")?\\(p.(?<protRef>"+ ProteinInput.THREE_LETTER_AA + ")(?<protPos>" + POS + ")(?<protAlt>" + ProteinInput.THREE_LETTER_AA+")\\))?";

    private static final String REGEX = REF_SEQ + HGVS.COLON + VAR_DESC;

    private static Pattern p1 = Pattern.compile(REF_SEQ, Pattern.CASE_INSENSITIVE);
    private static Pattern p2 = Pattern.compile(VAR_DESC, Pattern.CASE_INSENSITIVE);


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


    // Pre-check (level 1)
    // Pattern: NM_?:c.?
    public static boolean preCheck(String inputStr) {
        return HGVS.preCheck(PREFIX, SCHEME, inputStr);
    }

    public static boolean startsWithPrefix(String inputStr) {
        return HGVS.startsWithPrefix(PREFIX, inputStr);
    }

    public static HGVSc parse(String inputStr) {
        // pre-condition:
        // [x] fits general HGVS format - ref seq & var desc part separated by a single colon
        // [x] starts with HGVSc prefix

        HGVSc parsedInput = new HGVSc(inputStr);
        try {
            String[] params = inputStr.split(HGVS.COLON);
            if (params.length == 2) {
                String refSeqPart = params[0];
                String variantDescPart = params[1];

                Matcher m1 = p1.matcher(refSeqPart);
                if (m1.matches()) {
                    String rsAcc = m1.group("rsAcc"); // refseq accession
                    // optional gene
                    String gene = m1.group("gene");
                    parsedInput.setRsAcc(rsAcc);
                    parsedInput.setGene(gene);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_INVALID_REFSEQ);
                }

                Matcher m2 = p2.matcher(variantDescPart);
                if (m2.matches()) {

                    String pos = m2.group("pos");
                    String ref = m2.group("ref");
                    String alt = m2.group("alt");

                    parsedInput.setPos(Integer.parseInt(pos));
                    parsedInput.setRef(ref);
                    parsedInput.setAlt(alt);

                    // optional fields
                    String protRef = m2.group("protRef");
                    String protAlt = m2.group("protAlt");
                    String protPos = m2.group("protPos");

                    parsedInput.setProtRef(protRef);
                    parsedInput.setProtAlt(protAlt);
                    parsedInput.setProtPos(protPos == null ? null : Integer.parseInt(protPos));

                } else {
                    parsedInput.addError(ErrorConstants.HGVS_INVALID_VAR_DESC_C);

                    if (!variantDescPart.startsWith(SCHEME)) {
                        parsedInput.addError(ErrorConstants.HGVS_REFSEQ_C_SCHEME_MISMATCH);
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



    @Override
    public String toString() {
        return String.format("HGVSc [inputStr=%s]", getInputStr());
    }

}
