package uk.ac.ebi.protvar.service;

import org.springframework.core.task.AsyncTaskExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
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
            "^([ \\t]*(?:chr)?(?:\\d{1,2}|X|Y|MT?)[: \\t]+)(\\d{1,3}(?:,\\d{3})+)(?!\\d)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static List<String> normalizeInput(String rawInput) {
        // remove thousands separators first, so the comma split below doesn't break the position
        String input = POSITION_WITH_SEPARATORS.matcher(rawInput).replaceAll(m ->
                Matcher.quoteReplacement(m.group(1) + m.group(2).replace(",", "")));
        return Arrays.stream(input.split("\\R|,")) // split on any line break, pipe and comma
                .map(String::trim)          // trim each line
                .map(InputService::stripQuotes)
                .filter(line -> !line.isEmpty())      // remove blank lines
                .filter(line -> !line.startsWith("#")) // ignore comment lines
                .collect(Collectors.toList());
    }

    // "P22304 A205P" or 'P22304 A205P' -> P22304 A205P
    private static String stripQuotes(String line) {
        if (line.length() >= 2) {
            char first = line.charAt(0);
            if ((first == '"' || first == '\'') && line.charAt(line.length() - 1) == first)
                return line.substring(1, line.length() - 1).trim();
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
