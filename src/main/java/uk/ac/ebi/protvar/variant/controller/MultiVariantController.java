package uk.ac.ebi.protvar.variant.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.variant.VariantInput;
import uk.ac.ebi.protvar.variant.dto.VariantPageResponse;
import uk.ac.ebi.protvar.variant.dto.VariantResponse;
import uk.ac.ebi.protvar.variant.parser.VariantInputParser;
import uk.ac.ebi.protvar.variant.service.VariantService;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/*
Use Case 1: Single Variant Lookup Page
/variant/{encodedVariant}
Examples:
/variant/rs123
/variant/chr13-32914438-C-T
/variant/NM_007294.3%3Ac.68_69delAG
/variant/P12345-456-K-M
+ Optional query param
/variant/{encodedVariant}?format=HGVS_C

Use Case 2: Multi-Variant Submission Page
/variants/{submissionId}
Example:
/variants/abc123

Use Case	        URL Pattern	                    Notes
Single variant view	/variant/{encodedVariant}       SEO-friendly, concise, optionally add ?format=
                                                    to disambiguate format (instead of type which is deducible from format)
List view (UI)	    /variants?submissionId=xyz or   List managed server-side
                    /variants/user/abc
API - parse	        POST /api/variants/parse	    Accepts JSON body
API - annotate	    POST /api/variants/annotate	    For full processing + results
 */

@RestController
@RequestMapping("/variants")
@RequiredArgsConstructor
@Hidden
public class MultiVariantController {
    private final VariantInputParser variantInputParser;
    private final VariantService variantService;

    @PostMapping
    public ResponseEntity<VariantPageResponse> submitVariants(
            @RequestBody List<String> inputs,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        //List<VariantInput> parsed = variantInputParser.parseInputs(inputs);
        List<VariantInput> parsed = IntStream.range(0, inputs.size())
                .mapToObj(i -> VariantInput.parse(inputs.get(i)))
                .collect(Collectors.toList());

        int total = parsed.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        List<VariantResponse> results = parsed.subList(from, to).stream()
                .map(vi -> new VariantResponse(vi, variantService.annotate(vi)))
                .toList();

        VariantPageResponse response = new VariantPageResponse(results, total, page, size, (int) Math.ceil((double) total / size));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{submissionId}") // currently referred to as {inputId}
    public ResponseEntity<VariantPageResponse> getSubmittedVariants(@PathVariable String submissionId) {
        // TODO implement similar logic as submitVariants but fetch from storage
        VariantPageResponse response = variantService.getSubmissionById(submissionId);
        return response == null ? ResponseEntity.notFound().build() :
            ResponseEntity.ok(response);
    }

}
