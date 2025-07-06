package uk.ac.ebi.protvar.input.processor;

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
            if (DbsnpID.startsWithPrefix(inputLine))
                return DbsnpID.parse(inputLine);

            if (ClinVarID.startsWithPrefix(inputLine))
                return ClinVarID.parse(inputLine);

            if (CosmicID.startsWithPrefix(inputLine))
                return CosmicID.parse(inputLine);
        }

        /**
         * HGVS general pattern _:(S?)[a-z]._
         *                     /            \
         *                 REF_SEQ        VAR_DESC
         */
        if (HGVS.matchesPattern(inputLine)) {
            if (HGVSg.matchesPattern(inputLine))
                return HGVSg.parse(inputLine);

            if (HGVSc.matchesPattern(inputLine))
                return HGVSc.parse(inputLine);

            if (HGVSp.matchesPattern(inputLine))
                return HGVSp.parse(inputLine);

            return HGVS.invalid(inputLine);
        }

        if (ProteinInput.startsWithAccession(inputLine))
            return ProteinInput.parse(inputLine);

        if (GenomicInput.startsWithChromo(inputLine)) {
            if (Gnomad.matchesPattern(inputLine)) // ^chr-pos-ref-alt$
                return Gnomad.parse(inputLine);

            if (VCF.matchesPattern(inputLine)) // ^chr pos id ref alt...
                return VCF.parse(inputLine);

            if (GenomicInput.matchesPattern(inputLine)) // ^chr pos( ref( alt)?)?$
                return GenomicInput.parse(inputLine);
        }
        return GenomicInput.invalid(inputLine); // default (or most common) input is expected to be genomic, so
        // let's assume any invalid input is of GenomicInput type.
    }

}
