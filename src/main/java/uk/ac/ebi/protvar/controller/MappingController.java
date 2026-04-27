package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.MappingService;
import uk.ac.ebi.protvar.types.IdentifierType;
import uk.ac.ebi.protvar.utils.InputTypeResolver;
import uk.ac.ebi.protvar.utils.MappingRequestValidator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.ac.ebi.protvar.constants.PageUtils.*;

@Tag(name = "Coordinate Mapping")
@RestController
@RequestMapping("/mapping")
@CrossOrigin
@RequiredArgsConstructor
public class MappingController {

    private final MappingService mappingService;

    /**
     * Direct variant query: GET /mapping?q=19-1010539-G-C[&assembly=GRCh38]
     */
    @Operation(summary = "Retrieve mappings for a single variant query string.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> queryVariant(
            @Parameter(description = "Variant query in any supported format.", example = "19-1010539-G-C")
            @RequestParam String q,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        MappingRequest request = MappingRequest.builder()
                .q(q)
                .assembly(assembly)
                .page(1)
                .pageSize(1)
                .build();
        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }

    /**
     * Uploaded result lookup: GET /mapping/{resultId}
     */
    @Operation(summary = "Retrieve mappings for an uploaded result by ID.")
    @GetMapping(value = "/{resultId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> queryResult(
            @Parameter(description = "Uploaded result ID (32-character hex).", example = "abc123...")
            @PathVariable("resultId") String resultId,
            @Parameter(description = MappingRequest.PAGE_DESC, example = PAGE)
            @RequestParam(value = "page", defaultValue = PAGE, required = false) Integer page,
            @Parameter(description = MappingRequest.PAGE_SIZE_DESC, example = PAGE_SIZE)
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) Integer pageSize,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {

        if (page < 1) page = DEFAULT_PAGE;
        if (pageSize < PAGE_SIZE_MIN || pageSize > PAGE_SIZE_MAX) pageSize = DEFAULT_PAGE_SIZE;

        MappingRequest request = MappingRequest.builder()
                .resultId(resultId)
                .page(page)
                .pageSize(pageSize)
                .assembly(assembly)
                .build();
        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }

    @Operation(summary = "Retrieve mappings for identifiers or apply filters (POST, JSON body).")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingJson(@Valid @RequestBody MappingRequest request) {
        return handleRequest(request);
    }

    @Operation(summary = "Retrieve mappings for identifiers or apply filters (POST, form body).")
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingForm(@Valid @RequestBody @ModelAttribute MappingRequest request) {
        return handleRequest(request);
    }

    /**
     * Central dispatch for POST /mapping requests.
     * Routing priority: resultId → ids[] → q → filter-only.
     * Identifier types with null type are auto-detected; ambiguous values fall back to GENE.
     */
    public ResponseEntity<?> handleRequest(MappingRequest request) {
        Optional<String> validationError = MappingRequestValidator.validate(request);
        if (validationError.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError.get()));
        }

        // resultId and q route directly — no pre-processing needed
        if (request.getResultId() != null || request.getQ() != null) {
            return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
        }

        // ids[]: auto-detect missing types
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            List<Identifier> resolvedIds = request.getIds().stream()
                    .map(id -> {
                        if (id.type() != null) return id;
                        IdentifierType detected = InputTypeResolver.resolveIdentifier(id.value());
                        return new Identifier(detected != null ? detected : IdentifierType.GENE, id.value());
                    })
                    .collect(Collectors.toList());
            request.setIds(resolvedIds);
        }

        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }
}
