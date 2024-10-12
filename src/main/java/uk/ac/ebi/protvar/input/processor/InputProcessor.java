package uk.ac.ebi.protvar.input.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.genomic.Gnomad;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.format.genomic.VCF;
import uk.ac.ebi.protvar.input.format.id.ClinVarID;
import uk.ac.ebi.protvar.input.format.id.CosmicID;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InputProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputProcessor.class);

    /**
     * Takes a list of input strings and return corresponding list of userInput objects
     * @param inputs
     * @return
     */
    public static List<UserInput> parse(List<String> inputs) {
        return inputs.stream()
                .filter(s -> s != null)                       // Remove null strings
                .map(String::trim)                            // Trim the strings
                .filter(s -> !s.isEmpty())                    // Remove empty strings (after trimming)
                .filter(s -> !s.startsWith("#"))              // Remove strings starting with '#' (comments)
                .map(InputProcessor::parse)
                .collect(Collectors.toList());
    }

    /**
     * Parse input string into a UserInput object.
     * @param inputStr	Null and empty inputs will have been filtered before calling this function.
     *             Input string will have been trimmed as well.
     * @return
     */
    public static UserInput parse(String inputStr) {
        if (inputStr == null || inputStr.isEmpty()) // prior checks will not make this be true
            return null;

        /** General Pattern
         * regex:
         *   ^XXX -> starts with XXX
         *   YYY$ -> ends with YYY
         *   (ZZZ)? -> optional ZZZ
         *
         * ID (single word - no space - and startsWithPrefix)
         *      caution: hgvs and gnomad also pass single word check!
         *  dbsnp       ^rs
         *  ClinVar     ^RCV|VCV
         *  COSMIC      ^COSV|COSM|COSN
         *
         * Genomic
         *  gnomAD      ^chr-pos-ref-alt$
         *  VCF         ^chr pos id ref alt...
         *  custom      ^chr pos( ref( alt)?)?$
         *                  note: ref(\s+|/|>)alt
         *                      ref & alt can be 1 letter base (ATCG)
         * Protein
         *  custom      ^acc (p.)?refPosAlt$    e.g. P22304 A205P, P07949 asn783thr
         *              ^acc pos( ref( alt)?)?$ e.g. P22309 71 (Gly (Arg)?)?
         *              ^acc pos ref/alt$       e.g. P22304 205 A/P
         *                  note: ref & alt can be 1- or 3-letter amino acid, and
         *                  alt Ter, *, =
         * HGVS
         *  General pattern  _:[a-z]._
         *  HGVS genomic     _:g._
         *  HGVS protein     _:p._
         *  HGVS codingDNA   _:c._
         *
         *  Unsupported formats:
         *  HGVS non-coding, mitochondrial, and RNA schemes (n., m., r.)
         *  Invalid scheme   _:*._ (anything else)
         *
         */

        // Steps:
        // L1 First-level check (top-level if condition) to see if input fits
        // a "general" pattern for e.g. _:[a-z]._ or starts with prefix
        // L2 Second-level check attempts a full parse to extract expected attributes
        // of the specific format/input type

        // General pattern checks may include
        // - single/multi-word check
        // - prefix check
        // - special character check e.g. :g.
        boolean singleWord = RegexUtils.WORD.matcher(inputStr).matches();

        if (singleWord) {
            // IDs should be single word input
            if (DbsnpID.startsWithPrefix(inputStr))
                return DbsnpID.parse(inputStr);

            if (ClinVarID.startsWithPrefix(inputStr))
                return ClinVarID.parse(inputStr);

            if (CosmicID.startsWithPrefix(inputStr))
                return CosmicID.parse(inputStr);
        }

        /**
         * HGVS general pattern _:(S?)[a-z]._
         *                     /            \
         *                 REF_SEQ        VAR_DESC
         */
        if (HGVS.matchesPattern(inputStr)) {
            if (HGVSg.matchesPattern(inputStr))
                return HGVSg.parse(inputStr);

            if (HGVSc.matchesPattern(inputStr))
                return HGVSc.parse(inputStr);

            if (HGVSp.matchesPattern(inputStr))
                return HGVSp.parse(inputStr);

            return HGVS.invalid(inputStr);
        }

        if (ProteinInput.startsWithAccession(inputStr))
            return ProteinInput.parse(inputStr);

        if (GenomicInput.startsWithChromo(inputStr)) {
            if (Gnomad.matchesPattern(inputStr)) // ^chr-pos-ref-alt$
                return Gnomad.parse(inputStr);

            if (VCF.matchesPattern(inputStr)) // ^chr pos id ref alt...
                return VCF.parse(inputStr);

            if (GenomicInput.matchesPattern(inputStr)) // ^chr pos( ref( alt)?)?$
                return GenomicInput.parse(inputStr);
        }
        return GenomicInput.invalid(inputStr); // default (or most common) input is expected to be genomic, so
        // let's assume any invalid input is of GenomicInput type.
    }

    /**
     * Summary of original input, that needs to be parsed.
     * @param originalInput
     * @return
     */
    public static InputSummary summary(String originalInput) {
        List<String> inputs = Arrays.asList(originalInput.split("\\R|,"));
        List<UserInput> userInputs = parse(inputs);
        return summary(userInputs);
    }

    /**
     * Summary of a list of parsed user inputs.
     * @param userInputs may be for an input partition or whole input
     * @return
     */
    public static InputSummary summary(List<UserInput> userInputs) {
        EnumMap<Type, Integer> inputCounts = new EnumMap<>(Type.class);
        for (Type type : Type.values()) {
            inputCounts.put(type, 0);
        }

        userInputs.stream().forEach(input -> {
            if (input.isValid() && input.getType() != null) {
                inputCounts.put(input.getType(), inputCounts.get(input.getType()) + 1);
            } else {
                inputCounts.put(Type.INVALID, inputCounts.get(Type.INVALID) + 1);
            }
        });
        return InputSummary.builder()
                .totalCount(userInputs.size())
                .inputCounts(inputCounts)
                .build();
    }
}
