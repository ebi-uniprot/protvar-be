package uk.ac.ebi.protvar.input.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.VariantType;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.genomic.GenomicParser;
import uk.ac.ebi.protvar.input.parser.genomic.GnomadParser;
import uk.ac.ebi.protvar.input.parser.genomic.VCFParser;
import uk.ac.ebi.protvar.input.parser.hgvs.HGVScParser;
import uk.ac.ebi.protvar.input.parser.hgvs.HGVSgParser;
import uk.ac.ebi.protvar.input.parser.hgvs.HGVSpParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinParser;
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarParser;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicParser;
import uk.ac.ebi.protvar.input.parser.variantid.DbsnpParser;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.List;
import java.util.stream.Collectors;

public class InputParser {
    protected static final Logger LOGGER = LoggerFactory.getLogger(InputParser.class);

    public static final String BASE = "(A|T|C|G)";
    public static final String POS = "([0-9]*[1-9][0-9]*)";  // positive-only integers incl. w/ leading zeros

    /**
     * Takes a list of input strings and return corresponding list of parsed variant input objects
     * @param inputs
     * @return
     */
    public static List<VariantInput> parse(List<String> inputs) {
        return inputs.stream()
                .filter(s -> s != null)                       // Remove null strings
                .map(String::trim)                            // Trim the strings
                .filter(s -> !s.isEmpty())                    // Remove empty strings (after trimming)
                .filter(s -> !s.startsWith("#"))              // Remove strings starting with '#' (comments)
                .map(InputParser::parse)
                .collect(Collectors.toList());
    }

    public static GenomicInput parseValidGenomicInput(String variant) {
        if (variant == null || variant.isBlank()) return null;

        VariantInput input = parse(variant);
        if (input.isValid() && input.getType() == VariantType.GENOMIC) {
            return (GenomicInput) input;
        }
        return null;
    }

    /**
     * Parse input string into a VariantInput object.
     * @param inputLine	Null and empty inputs will have been filtered before calling this function.
     *             Input string will have been trimmed as well.
     * @return
     */
    public static VariantInput parse(String inputLine) {
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
            if (DbsnpParser.startsWithPrefix(inputLine))
                return DbsnpParser.parse(inputLine);

            if (ClinvarParser.startsWithPrefix(inputLine))
                return ClinvarParser.parse(inputLine);

            if (CosmicParser.startsWithPrefix(inputLine))
                return CosmicParser.parse(inputLine);
        }

        /**
         * HGVS general pattern _:(S?)[a-z]._
         *                     /            \
         *                 REF_SEQ        VAR_DESC
         */
        if (HGVS.matchesPattern(inputLine)) {
            if (HGVSgParser.matchesPattern(inputLine))
                return HGVSgParser.parse(inputLine);

            if (HGVScParser.matchesPattern(inputLine))
                return HGVScParser.parse(inputLine);

            if (HGVSpParser.matchesPattern(inputLine))
                return HGVSpParser.parse(inputLine);

            return HGVS.invalid(inputLine);
        }

        if (ProteinParser.startsWithAccession(inputLine))
            return ProteinParser.parse(inputLine);

        if (GenomicParser.startsWithChromo(inputLine)) {
            if (GnomadParser.matchesPattern(inputLine)) // ^chr-pos-ref-alt$
                return GnomadParser.parse(inputLine);

            if (VCFParser.matchesPattern(inputLine)) // ^chr pos id ref alt...
                return VCFParser.parse(inputLine);

            if (GenomicParser.matchesPattern(inputLine)) // ^chr pos( ref( alt)?)?$
                return GenomicParser.parse(inputLine);
        }
        return GenomicInput.invalid(inputLine); // default (or most common) input is expected to be genomic, so
        // let's assume any invalid input is of GenomicInput type.
    }
}
