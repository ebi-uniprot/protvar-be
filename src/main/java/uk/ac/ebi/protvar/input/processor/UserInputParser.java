package uk.ac.ebi.protvar.input.processor;

import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.parser.hgvs.HGVScInputParser;
import uk.ac.ebi.protvar.input.parser.hgvs.HGVSgInputParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinInputParser;
import uk.ac.ebi.protvar.input.parser.genomic.GenomicInputParser;
import uk.ac.ebi.protvar.input.parser.genomic.GnomadInputParser;
import uk.ac.ebi.protvar.input.parser.genomic.VCFInputParser;
import uk.ac.ebi.protvar.input.parser.hgvs.HGVSpInputParser;
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarInputParser;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicInputParser;
import uk.ac.ebi.protvar.input.parser.variantid.DbsnpInputParser;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.List;
import java.util.stream.Collectors;

public class UserInputParser {
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
                .map(UserInputParser::parse)
                .collect(Collectors.toList());
    }


    public static GenomicInput parseValidGenomicInput(String variant) {
        if (variant == null || variant.isBlank()) return null;

        UserInput input = parse(variant);
        if (input.isValid() && input.getType() == Type.GENOMIC) {
            return (GenomicInput) input;
        }
        return null;
    }

    /**
     * Parse input string into a UserInput object.
     * @param inputLine	Null and empty inputs will have been filtered before calling this function.
     *             Input string will have been trimmed as well.
     * @return
     */
    public static UserInput parse(String inputLine) {
        if (inputLine == null || inputLine.isEmpty()) // prior checks will not make this be true
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
         *  internal    ^chr pos( ref( alt)?)?$
         *                  note: ref(\s+|/|>)alt
         *                      ref & alt can be 1 letter base (ATCG)
         * Protein
         *  internal    ^acc (p.)?refPosAlt$    e.g. P22304 A205P, P07949 asn783thr
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
        boolean singleWord = RegexUtils.WORD.matcher(inputLine).matches();

        if (singleWord) {
            // IDs should be single word input
            if (DbsnpInputParser.startsWithPrefix(inputLine))
                return DbsnpInputParser.parse(inputLine);

            if (ClinvarInputParser.startsWithPrefix(inputLine))
                return ClinvarInputParser.parse(inputLine);

            if (CosmicInputParser.startsWithPrefix(inputLine))
                return CosmicInputParser.parse(inputLine);
        }

        /**
         * HGVS general pattern _:(S?)[a-z]._
         *                     /            \
         *                 REF_SEQ        VAR_DESC
         */
        if (HGVS.matchesPattern(inputLine)) {
            if (HGVSgInputParser.matchesPattern(inputLine))
                return HGVSgInputParser.parse(inputLine);

            if (HGVScInputParser.matchesPattern(inputLine))
                return HGVScInputParser.parse(inputLine);

            if (HGVSpInputParser.matchesPattern(inputLine))
                return HGVSpInputParser.parse(inputLine);

            return HGVS.invalid(inputLine);
        }

        if (ProteinInputParser.startsWithAccession(inputLine))
            return ProteinInputParser.parse(inputLine);

        if (GenomicInputParser.startsWithChromo(inputLine)) {
            if (GnomadInputParser.matchesPattern(inputLine)) // ^chr-pos-ref-alt$
                return GnomadInputParser.parse(inputLine);

            if (VCFInputParser.matchesPattern(inputLine)) // ^chr pos id ref alt...
                return VCFInputParser.parse(inputLine);

            if (GenomicInputParser.matchesPattern(inputLine)) // ^chr pos( ref( alt)?)?$
                return GenomicInputParser.parse(inputLine);
        }
        return GenomicInput.invalid(inputLine); // default (or most common) input is expected to be genomic, so
        // let's assume any invalid input is of GenomicInput type.
    }

}
