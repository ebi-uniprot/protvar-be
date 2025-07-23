package uk.ac.ebi.protvar.variant.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.variant.VariantInput;
import uk.ac.ebi.protvar.variant.service.VariantService;
import uk.ac.ebi.protvar.variant.dto.VariantAnnotation;
import uk.ac.ebi.protvar.variant.dto.VariantResponse;

@RestController
@RequestMapping("/variant")
@RequiredArgsConstructor
@Hidden
public class SingleVariantController {
    private final VariantService variantService;

    @GetMapping("/{input}")
    public ResponseEntity<VariantResponse> getSingleVariant(
            @PathVariable String input,
            @RequestParam(value = "format", required = false) String format // optional
    ) {
        VariantInput variantInput = VariantInput.parse(input, format);

        if (!variantInput.valid()) {
            return ResponseEntity.badRequest().body(new VariantResponse(variantInput, null));
        }
        // Fetch annotations, mappings, etc.
        VariantAnnotation annotation = variantService.annotate(variantInput);

        return ResponseEntity.ok(new VariantResponse(variantInput, annotation));
    }
}
