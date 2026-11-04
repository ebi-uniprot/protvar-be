package uk.ac.ebi.protvar.service;

import org.springframework.core.task.AsyncTaskExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.VariantType;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.types.Assembly;
import uk.ac.ebi.protvar.utils.ChecksumUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InputService {
    private final AsyncTaskExecutor inputExecutor; // bean resolved by name (ExecutorConfig)
    private final UploadCacheService cacheService;
    private final UploadFilterService uploadFilterService;
    private final BuildProcessor buildProcessor;

    private String checksumFromLines(List<String> lines) {
        String joined = String.join("\n", lines);
        return ChecksumUtils.checksum(joined.getBytes(StandardCharsets.UTF_8));
    }

    public String processInput(InputRequest request) {
        List<String> normalizedLines = normalizeInput(request.getRawInput()); // ensure consistent checksums across semantically identical inputs
        String id = checksumFromLines(normalizedLines);
        // Launch async job to cache, determine build and summarise input
        inputExecutor.submit(() -> {
            cacheService.cacheInput(id, normalizedLines); // cache the normalized input lines

            if (request.isAutoDetectBuild()) {
                InputBuild build = detectBuild(normalizedLines);
                cacheService.cacheBuild(id, build);
            }

            List<VariantInput> parsed = normalizedLines.stream()
                    .map(VariantParser::parse)
                    .collect(Collectors.toList());

            InputSummary summary = summarize(parsed);
            cacheService.cacheSummary(id, summary);

            // prepare the upload now (summary, notes, filters) rather than on its first filtered request
            uploadFilterService.prepare(id, request.getAssembly(), cacheService.getBuild(id), false);

        });
        return id;
    }

    // Genomic position written with thousands separators, e.g. chr16:29,847,799 T>C
    private static final Pattern POSITION_WITH_SEPARATORS = Pattern.compile(
            "^((?:chr)?(?:\\d{1,2}|X|Y|MT?)[: \\t]+)(\\d{1,3}(?:,\\d{3})+)(?!\\d)", Pattern.CASE_INSENSITIVE);

    // VCF line with several ALT alleles: CHROM POS ID REF ALT1,ALT2[,...] [other columns]
    private static final Pattern VCF_MULTI_ALT = Pattern.compile(
            "^(\\S+\\s+\\S+\\s+\\S+\\s+[ACGT]\\s+)([ACGT](?:,[ACGT])+)(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);

    /**
     * Splits raw input into variants: one per line (a line break is the only separator; commas and pipes can be
     * part of a variant). Blank lines and # comment lines are dropped; each line goes through normalizeLine.
     */
    public static List<String> normalizeInput(String rawInput) {
        return Arrays.stream(rawInput.split("\\R"))
                .flatMap(line -> normalizeLine(line).stream())
                .collect(Collectors.toList());
    }

    /**
     * One input line → its variant(s), in a canonical form (consistent upload checksums):
     * wrapping quotes removed; thousands separators removed from a genomic position; a VCF line with several
     * ALT alleles expanded to one line per allele; any other comma read as a column separator (a CSV row,
     * e.g. 7,140753336,A,T → 7 140753336 A T). Empty for a blank or # comment line.
     */
    public static List<String> normalizeLine(String rawLine) {
        String line = stripQuotes(rawLine.trim());
        if (line.isEmpty() || line.startsWith("#")) return List.of();
        line = POSITION_WITH_SEPARATORS.matcher(line).replaceFirst(m ->
                Matcher.quoteReplacement(m.group(1) + m.group(2).replace(",", "")));
        Matcher multiAlt = VCF_MULTI_ALT.matcher(line);
        if (multiAlt.matches()) {
            String prefix = multiAlt.group(1);
            return Arrays.stream(multiAlt.group(2).split(",")).map(alt -> prefix + alt).collect(Collectors.toList());
        }
        if (line.contains(",") && !commasOnlyInVcfExtraColumns(line)) {
            line = Arrays.stream(line.split(","))
                    .map(f -> stripQuotes(f.trim()))
                    .filter(f -> !f.isEmpty())
                    .collect(Collectors.joining(" "));
        }
        return line.isEmpty() ? List.of() : List.of(line);
    }

    // CHROM POS ID REF ALT INFO...: commas only after the 5th column (e.g. AF=0.1,0.2), which VCF parsing ignores
    private static boolean commasOnlyInVcfExtraColumns(String line) {
        String[] cols = line.split("\\s+", 6);
        return cols.length == 6 && Arrays.stream(cols, 0, 5).noneMatch(c -> c.contains(","));
    }

    // "P22304 A205P" or 'P22304 A205P' -> P22304 A205P
    private static String stripQuotes(String line) {
        if (line.length() >= 2) {
            char first = line.charAt(0);
            String inner = line.substring(1, line.length() - 1);
            // not "7","140753336" (a CSV row of quoted fields: its quotes go field by field)
            if ((first == '"' || first == '\'') && line.charAt(line.length() - 1) == first && inner.indexOf(first) < 0)
                return inner.trim();
        }
        return line;
    }

    /** Accepted values: The assembly can be one of the following:
     *  - null, empty, anything else (treated as null or not provided)
     *  - AUTO, 37, or 38
     *
     *  Rules:
     *  - Use auto-detection only if explicitly specified as AUTO.
     *  - Perform build conversion if 37 is specified.
     *  - Default to 38 in all other cases, including when auto-detection is inconclusive.
     *
     *  Steps:
     *  1. Check the user-specified assembly.
     *  2. If AUTO, check if the input ID has a cached detected build;
     *      if yes, use it, otherwise, detect, cache, and use the detected build.
     *  3. If 37, convert to 38.
     *  4. If 38, no conversion is needed.
     *  5. In all other cases, no conversion is required. Defaults to 38.
     *
     *  Note:
     *  - BuildProcessor always converts hgvsGs37 inputs, if any.
     *  - The same input ID may be requested with a different assembly parameter in another
     *    request, so always verify the submitted assembly.
     */

    /**
     *  if assembly == AUTO
     *     if buildNotAlreadyDetermined
     *         return
     *     else
     *         determine, cache and return build
     *  else use provided build (37 or 38) or default to 38
     *
     * @param request
     * @return
     */

    public void detectBuild(InputRequest request) {
        if (request.isAutoDetectBuild()) {
            String id = request.getInputId();
            if (id != null &&
                    cacheService.getBuild(id) == null) {

                List<String> inputs = cacheService.getInput(id);
                if (inputs == null) return; // unknown or expired input id
                InputBuild build = detectBuild(inputs);
                cacheService.cacheBuild(id, build);
            }
        }
    }

    /**
     * Assembly of a single query (q) under AUTO: GRCh37 (to be converted, with PV-502) only when its reference
     * base matches GRCh37 and not GRCh38; otherwise null, i.e. GRCh38 as before, with no message (a single line
     * that matches both builds is common, and a warning on it would be noise). Needs a reference base.
     */
    public InputBuild detectQueryBuild(List<String> lines) {
        List<VariantInput> genomic = buildProcessor.filterGenomicInputs(lines).stream()
                .filter(i -> ((GenomicInput) i).getRefBase() != null)
                .collect(Collectors.toList());
        if (genomic.isEmpty()) return null;
        InputBuild build = buildProcessor.detect(genomic);
        return build.getAssembly() == Assembly.GRCH37 ? build : null;
    }

    private InputBuild detectBuild(List<String> allInputs) {
        List<VariantInput> genomicInputs = buildProcessor.filterGenomicInputs(allInputs);
        if (!genomicInputs.isEmpty()) {
            return buildProcessor.detect(genomicInputs); // returns a default (AUTO_DETECT_UNKNOWN)
        }
        return new InputBuild(Assembly.GRCH38, null);
        // default to GRCh38 if no genomic inputs found
        // (to avoid auto-detect to run again)
    }

    /**
     * Summary of a list of parsed user inputs.
     * @param inputs may be for an input partition or whole input
     * @return
     */
    public static InputSummary summarize(List<VariantInput> inputs) {
        EnumMap<VariantType, Integer> inputCounts = new EnumMap<>(VariantType.class);
        for (VariantType type : VariantType.values()) {
            inputCounts.put(type, 0);
        }

        inputs.stream().forEach(input -> {
            if (input.isValid() && input.getType() != null) {
                inputCounts.put(input.getType(), inputCounts.get(input.getType()) + 1);
            } else {
                inputCounts.put(VariantType.INVALID, inputCounts.get(VariantType.INVALID) + 1);
            }
        });
        return InputSummary.builder()
                .totalCount(inputs.size())
                .inputCounts(inputCounts)
                .build();
    }

}
