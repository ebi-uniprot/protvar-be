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
    public static final String PREFIX = "NM_"; // not necessary any more!
    public static final String SCHEME = "c.";

    public static final String POS = GenomicInput.POS;
    public static final String GENE = "[A-Z][A-Z0-9]+"; // MAIN CRITERIA: must only contain uppercase letters and numbers, start with a letter
    public static final String SCHEME_PATTERN_REGEX = ":(\\s+)?c\\.";

    public static final String GENERAL_HGVS_C_PATTERN_REGEX = "^(?<refSeq>[^:]+)"+SCHEME_PATTERN_REGEX+"(?<varDesc>[^:]+)$";

    private static final String REF_SEQ_REGEX =
            //"(?<rsAcc>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")" + // RefSeq NM accession
            "(?<rsAcc>(NM_|NP_)" + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")" +
                    // OPTIONALLY, gene name
                    "(("+ RegexUtils.SPACES +")?\\((?<gene>"+GENE+")\\))?";

    private static final String VAR_DESC_REGEX_ =
                    "(?<ref>" + GenomicInput.BASE + ")" +
                    "(" + HGVS.SUB_SIGN + ")" +
                    "(?<alt>" + GenomicInput.BASE + ")" +
                    // OPTIONALLY, with or without space followed by protein substitution
                    "(("+ RegexUtils.SPACES +")?" +
                    "(p.\\(|\\(p.)" + // lenient on where the opening bracket is
                    "(?<protRef>"+ ProteinInput.AMINO_ACID_REF3 + ")" +
                    "(?<protPos>" + POS + ")" +
                    "(?<protAlt>" + ProteinInput.AMINO_ACID_ALT3 +")\\))?";

    private static final String VAR_DESC_REGEX =
            "(?<pos>" + POS + ")" + VAR_DESC_REGEX_;
    private static Pattern GENERAL_PATTERN = Pattern.compile(GENERAL_HGVS_C_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern REF_SEQ_PATTERN = Pattern.compile(REF_SEQ_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern VAR_DESC_PATTERN = Pattern.compile(VAR_DESC_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String REGEX_5_PRIME_UTR = "-(?<pos>" + POS + ")";  // 5' UTR
    private static final String REGEX_INTRON_5_SIDE = "(?<pos>" + POS + ")\\+(?<offset>[1-9]\\d*)"; // Intron, 5' side
    private static final String REGEX_INTRON_3_SIDE = "(?<pos>" + POS + ")-(?<offset>[1-9]\\d*)";  // Intron, 3' side
    private static final String REGEX_3_PRIME_UTR = "\\*(?<pos>" + POS + ")"; // 3' UTR

    private static final Pattern PATTERN_5_PRIME_UTR = Pattern.compile(REGEX_5_PRIME_UTR + VAR_DESC_REGEX_);
    private static final Pattern PATTERN_INTRON_5_SIDE = Pattern.compile(REGEX_INTRON_5_SIDE + VAR_DESC_REGEX_);
    private static final Pattern PATTERN_INTRON_3_SIDE = Pattern.compile(REGEX_INTRON_3_SIDE + VAR_DESC_REGEX_);
    private static final Pattern PATTERN_3_PRIME_UTR = Pattern.compile(REGEX_3_PRIME_UTR + VAR_DESC_REGEX_);

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

    public static boolean matchesPattern(String input) {
        return input.matches(GENERAL_HGVS_C_PATTERN_REGEX);
    }

    public static HGVSc parse(String inputStr) {
        // pre-condition: fits _:(S?)c._ pattern
        HGVSc parsedInput = new HGVSc(inputStr);
        try {
            Matcher generalMatcher = GENERAL_PATTERN.matcher(inputStr);
            if (generalMatcher.matches()) {
                String refSeq = generalMatcher.group("refSeq");

                Matcher refSeqMatcher = REF_SEQ_PATTERN.matcher(refSeq);
                if (refSeqMatcher.matches()) {
                    String rsAcc = refSeqMatcher.group("rsAcc"); // refseq accession
                    // optional gene
                    String gene = refSeqMatcher.group("gene");
                    parsedInput.setRsAcc(rsAcc);
                    parsedInput.setGene(gene);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_C_REFSEQ_INVALID);
                }

                String varDesc = generalMatcher.group("varDesc");
                Matcher varDescMatcher = VAR_DESC_PATTERN.matcher(varDesc);
                if (varDescMatcher.matches()) {
                    String pos = varDescMatcher.group("pos");
                    String ref = varDescMatcher.group("ref");
                    String alt = varDescMatcher.group("alt");

                    parsedInput.setPos(Integer.parseInt(pos));
                    parsedInput.setRef(ref);
                    parsedInput.setAlt(alt);

                    // Optional fields
                    String protRef = varDescMatcher.group("protRef");
                    String protAlt = varDescMatcher.group("protAlt");
                    if (protAlt != null && protAlt.equals("=")) {
                        protAlt = protRef;
                    }
                    String protPos = varDescMatcher.group("protPos");

                    parsedInput.setProtRef(protRef);
                    parsedInput.setProtAlt(protAlt);
                    parsedInput.setProtPos(protPos == null ? null : Integer.parseInt(protPos));

                } else {
                    if (PATTERN_5_PRIME_UTR.matcher(varDesc).matches()) {
                        parsedInput.addError("5' UTR positions are not supported (e.g., c.-128A>G).");
                    } else if (PATTERN_INTRON_5_SIDE.matcher(varDesc).matches()) {
                        parsedInput.addError("Intronic positions (5' side) are not supported (e.g., c.128+1G>A).");
                    } else if (PATTERN_INTRON_3_SIDE.matcher(varDesc).matches()) {
                        parsedInput.addError("Intronic positions (3' side) are not supported (e.g., c.128-1G>A).");
                    } else if (PATTERN_3_PRIME_UTR.matcher(varDesc).matches()) {
                        parsedInput.addError("3' UTR positions are not supported (e.g., c.*128A>G).");
                    } else {
                        parsedInput.addError(ErrorConstants.HGVS_C_VARDESC_INVALID);
                    }
                }
            } else {
                throw new InvalidInputException("No match found.");
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
