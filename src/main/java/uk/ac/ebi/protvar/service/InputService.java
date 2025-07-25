package uk.ac.ebi.protvar.service;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InputService {
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    private final InputCacheService cacheService;
    private final BuildProcessor buildProcessor;

    private String checksumFromLines(List<String> lines) {
        String joined = String.join("\n", lines);
        return ChecksumUtils.checksum(joined.getBytes(StandardCharsets.UTF_8));
    }

    public String processInput(InputRequest request) {
        List<String> normalizedLines = normalizeInput(request.getRawInput()); // ensure consistent checksums across semantically identical inputs
        String id = checksumFromLines(normalizedLines);
        // Launch async job to cache, determine build and summarise input
        executorService.submit(() -> {
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

        });
        return id;
    }

    public static List<String> normalizeInput(String rawInput) {
        return Arrays.stream(rawInput.split("\\R|,")) // split on any line break, pipe and comma
                .map(String::trim)          // trim each line
                .filter(line -> !line.isEmpty())      // remove blank lines
                .filter(line -> !line.startsWith("#")) // ignore comment lines
                .collect(Collectors.toList());
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
