package uk.ac.ebi.protvar.input.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.*;
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
import uk.ac.ebi.protvar.input.parser.hgvs.HGVS;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VariantParser {
    protected static final Logger LOGGER = LoggerFactory.getLogger(VariantParser.class);

    // Standard DNA bases (pattern case insensitive flag handles both cases)
    public static final String VALID_BASE = "[ACGT]";

    // Position: allow leading zeros but must be > 0 when parsed
    public static final String VALID_POSITION = "[0-9]*[1-9][0-9]*";

    // Chromosome pattern with optional "chr" and mitochondrial aliases
    // - Optional chr prefix for: chromosomes 1-22 (with optional leading zeros), X, Y, M, MT
    // - No prefix for longer mitochondrial aliases: mit, mtDNA, mitochondria, mitochondrion
    public static final String VALID_CHROMOSOME = "(?:chr)?(?:0*(?:[1-9]|1[0-9]|2[0-2])|[XY]|M|MT)|mit|mtDNA|mitochondria|mitochondrion";


    /**
     * Takes a list of input strings and return corresponding list of parsed variant input objects
     *
     * inputs = [
     *   null,         // index 0 (filtered out)
     *   " # comment", // index 1 (filtered out)
     *   "abc",        // index 2 (kept)
     *   "  ",         // index 3 (filtered out)
     *   "def"         // index 4 (kept)
     * ]
     *
     * @param inputs
     * @return
     */
    public static List<VariantInput> parse(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of(); // Or throw exception if required
        }

        return IntStream.range(0, inputs.size())
                .filter(i -> inputs.get(i) != null)                      // remove nulls
                .filter(i -> !inputs.get(i).trim().isEmpty())            // remove empty (trimmed)
                .filter(i -> !inputs.get(i).trim().startsWith("#"))      // remove comments
                .mapToObj(i -> {
                    VariantInput input = VariantParser.parse(inputs.get(i).trim());
                    input.setOriginalIndex(i);
                    return input;
                })
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
     * @param inputStr	Null and empty inputs will have been filtered before calling this function.
     *             Input string will have been trimmed as well.
     * @return
     */
    public static VariantInput parse(String inputStr) {
        if (inputStr == null || inputStr.isBlank()) // prior checks will not make this be true
            return invalid(inputStr, "Empty input");

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

        // None space
        // e.g. IDs - dbSNP, ClinVar, COSMIC
        //      Genomic - gnomad
        //      HGVS - g|p?
        // Space
        // e.g. Genomic - Internal: chr pos (ref ([/|>]alt))
        //                VCF
        //      Protein - Internal
        //      Lenient HGVS - _:(S?)[a-z]._

        try {
            boolean singleWord = RegexUtils.WORD.matcher(inputStr).matches();

            // === SINGLE WORD FORMATS ===
            if (singleWord) {
                // Order by specificity and frequency
                // Most specific patterns first to avoid false positives

                // 1. DbSNP - very specific pattern (rs + digits only)
                if (DbsnpParser.matchesStructure(inputStr))
                    return DbsnpParser.parse(inputStr);

                // 2. ClinVar - specific pattern (RCV/VCV + 9 digits + optional version)
                if (ClinvarParser.matchesStructure(inputStr))
                    return ClinvarParser.parse(inputStr);

                // 3. COSMIC - less specific (COS* + any digits)
                if (CosmicParser.matchesStructure(inputStr))
                    return CosmicParser.parse(inputStr);
            }


            // === HGVS FORMATS ===
            // General pattern _:(S?)[a-z]._
            //                /            \
            //            REF_SEQ        VAR_DESC
            if (HGVS.matchesStructure(inputStr)) {
                // Order by specificity: most specific to least specific

                // Protein parser - most specific (requires specific AA patterns)
                if (HGVSpParser.matchesStructure(inputStr)) {
                    return HGVSpParser.parse(inputStr);
                }

                // Coding parser - medium specificity (allows optional protein annotation)
                if (HGVScParser.matchesStructure(inputStr)) {
                    return HGVScParser.parse(inputStr);
                }

                // Genomic parser - least specific (simple base substitution)
                if (HGVSgParser.matchesStructure(inputStr)) {
                    return HGVSgParser.parse(inputStr);
                }

                // If it looks like HGVS but doesn't match any specific parser
                return HGVS.invalid(inputStr);
            }

            // === OTHER FORMATS ===
            if (ProteinParser.matchesStructure(inputStr))
                return ProteinParser.parse(inputStr);

            if (GnomadParser.matchesPattern(inputStr))
                return GnomadParser.parse(inputStr);

            if (VCFParser.matchesPattern(inputStr))
                return VCFParser.parse(inputStr);

            if (GenomicParser.matchesPattern(inputStr))
                return GenomicParser.parse(inputStr);

            return invalid(inputStr, "Unsupported format");
        } catch (Exception e) {
            return invalid(inputStr, ErrorConstants.INVALID_GENERIC_INPUT); // todo review this error message
        }
    }

    public static VariantInput invalid(String inputStr, String message) {
        VariantInput invalid = new VariantInput(VariantFormat.INVALID, inputStr);
        invalid.addError(message);
        return invalid;
    }

    public static VariantInput invalid(String inputStr, ErrorConstants error) {
        VariantInput invalid = new VariantInput(VariantFormat.INVALID, inputStr);
        invalid.addError(error);
        return invalid;
    }

    public static String normalizeChr(String chr) {
        if (chr == null) return null;

        String normalized = chr.toLowerCase();

        // Remove 'chr' prefix if present
        if (normalized.startsWith("chr")) {
            normalized = normalized.substring(3);
        }

        // Handle mitochondrial aliases
        if (normalized.equals("m") || normalized.equals("mt") ||
                normalized.equals("mit") || normalized.equals("mtdna") ||
                normalized.equals("mitochondria") || normalized.equals("mitochondrion")) {
            return "MT";
        }

        // Handle X and Y
        if (normalized.equals("x")) return "X";
        if (normalized.equals("y")) return "Y";

        // Handle numeric chromosomes - remove leading zeros
        try {
            int chrNum = Integer.parseInt(normalized);
            if (chrNum >= 1 && chrNum <= 22) {
                return String.valueOf(chrNum);
            }
        } catch (NumberFormatException e) {
            // Not a number, return as-is (shouldn't happen with valid input)
        }

        return null; // or throw an exception for invalid chromosomes
    }

    public static String normalizeBase(String base) {
        return base == null ? null : base.toUpperCase();
    }
}
