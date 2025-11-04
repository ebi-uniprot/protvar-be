package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.SearchTerm;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.MappingService;
import uk.ac.ebi.protvar.validator.MappingRequestValidator;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.ac.ebi.protvar.constants.PageUtils.*;

@Tag(name = "Coordinate Mapping")
@RestController
@RequestMapping("/mapping")
@CrossOrigin
@RequiredArgsConstructor
public class MappingController {
    private static final String DESC = """
            Search terms can include:
            - Biological identifiers (UniProt accession, gene symbol, Ensembl ID, PDB ID, RefSeq ID)
            - Multiple identifiers can be specified
            - Omit search terms entirely for database-wide filtering
            
            Filters can be applied to refine results regardless of search terms.
            
            If `type` is not specified for a search term, the system will attempt to infer it automatically.
            """;
    private final Validator validator;
    private final MappingRequestValidator requestValidator;
    private final MappingService mappingService;

    /**
     * GET endpoint for single variant queries.
     * Convenient direct access for variant lookups.
     *
     * Example: /mapping?input=19-1010539-G-C&assembly=GRCh38
     * Example without assembly (defaults to AUTO): /mapping?input=19-1010539-G-C
     *
     * vs. using @GetMapping("/{input}") with @PathVariable String input:
     * /mapping/19-1010539-G-C
     *
     * @param input
     * @param assembly
     * @return
     */
    @Operation(summary = "Retrieve mappings for a single variant input - used for direct query.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> singleVariant(
            @Parameter(description = "Single variant query in a supported format.",
                    example = "19-1010539-G-C",
                    required = true)
            @RequestParam String input,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        // Create request with single variant search term
        MappingRequest request = MappingRequest.builder()
                .searchTerms(List.of(SearchTerm.variant(input)))
                .assembly(assembly)
                .page(1)
                .pageSize(1)
                .build();
        PagedMappingResponse response = mappingService.get(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * GET endpoint for input ID queries.
     * Retrieves previously submitted variant lists by their unique ID.
     *
     * Example: /mapping/abc123def456...?page=1&pageSize=50
     */
    @Operation(summary = "Retrieve mappings for a previously submitted variant list")
    @GetMapping(value = "/{inputId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getInputIdMapping(
            @Parameter(
                    description = "The unique 32-character ID of a previously submitted variant list",
                    example = "abc123def456..."
            )
            @PathVariable("inputId") String inputId,
            @Parameter(description = MappingRequest.PAGE_DESC, example = PAGE)
            @RequestParam(value = "page", defaultValue = PAGE, required = false) Integer page,
            @Parameter(description = MappingRequest.PAGE_SIZE_DESC, example = PAGE_SIZE)
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) Integer pageSize,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {

        // Validate and normalize pagination parameters
        if (page < 1) page = DEFAULT_PAGE;
        if (pageSize < PAGE_SIZE_MIN || pageSize > PAGE_SIZE_MAX) pageSize = DEFAULT_PAGE_SIZE;

        // Create request with input ID search term
        MappingRequest request = MappingRequest.builder()
                .searchTerms(List.of(SearchTerm.inputId(inputId)))
                .page(page)
                .pageSize(pageSize)
                .assembly(assembly)
                .build();

        Set<ConstraintViolation<MappingRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            List<String> errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.toList());

            return ResponseEntity.badRequest().body(errors);
        }

        return handleRequest(request);
    }

    /**
     * POST endpoint for advanced queries.
     * Supports:
     * - Multiple identifiers (UniProt, gene, Ensembl, PDB, RefSeq)
     * - Filter-only queries (no search terms)
     * - Combination of identifiers and filters
     */
    //@Operation(summary = POST_SUMMARY)
    @Operation(
            summary = "Advanced search with filtering capabilities.",
            description = DESC,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search and filter variants with various criteria",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "Simple UniProt search",
                                            summary = "Search all known variants for a UniProt accession",
                                            value = """
                        {
                          "searchTerms": [{"value": "P22304", "type": "uniprot"}]
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Gene with deleterious variants",
                                            summary = "Find highly deleterious BRCA1 variants sorted by CADD score",
                                            value = """
                        {
                          "searchTerms": [{"value": "BRCA1", "type": "gene"}],
                          "cadd": ["PROBABLY_DELETERIOUS", "HIGHLY_LIKELY_DELETERIOUS"],
                          "sort": "cadd",
                          "order": "desc"
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Highly conserved pathogenic variants",
                                            summary = "Find highly conserved AlphaMissense pathogenic variants with experimental structures",
                                            value = """
                        {
                          "searchTerms": [{"value": "P22304", "type": "uniprot"}],
                          "conservationMin": 0.8,
                          "am": ["PATHOGENIC"],
                          "experimentalModel": true
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Rare destabilizing variants",
                                            summary = "Find very rare variants that destabilize protein structure in binding pockets",
                                            value = """
                        {
                          "searchTerms": [{"value": "BRCA1", "type": "gene"}],
                          "alleleFreq": ["VERY_RARE"],
                          "stability": ["LIKELY_DESTABILISING"],
                          "pocket": true
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Filter-only search",
                                            summary = "Find all very rare, severe PopEVE variants in functional domains (no specific gene/protein)",
                                            value = """
                        {
                          "alleleFreq": ["VERY_RARE"],
                          "popeve": ["SEVERE"],
                          "functionalDomain": true,
                          "pageSize": 50
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Multiple identifiers",
                                            summary = "Search variants across multiple proteins/genes",
                                            value = """
                        {
                          "searchTerms": [
                            {"value": "P22304", "type": "uniprot"},
                            {"value": "BRCA1", "type": "gene"},
                            {"value": "ENSG00000012048", "type": "ensembl"}
                          ],
                          "known": true
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Complex multi-filter query",
                                            summary = "Advanced filtering with multiple criteria",
                                            value = """
                        {
                          "searchTerms": [{"value": "TP53", "type": "gene"}],
                          "alleleFreq": ["VERY_RARE", "RARE"],
                          "cadd": ["QUITE_LIKELY_DELETERIOUS", "PROBABLY_DELETERIOUS", "HIGHLY_LIKELY_DELETERIOUS"],
                          "am": ["PATHOGENIC"],
                          "popeve": ["SEVERE", "MODERATELY_DELETERIOUS"],
                          "conservationMin": 0.7,
                          "experimentalModel": true,
                          "interact": true,
                          "stability": ["LIKELY_DESTABILISING"],
                          "sort": "cadd",
                          "order": "desc",
                          "pageSize": 100
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "ESM1b score range",
                                            summary = "Find variants with specific ESM1b scores (more deleterious range)",
                                            value = """
                        {
                          "searchTerms": [{"value": "P22304", "type": "uniprot"}],
                          "esm1bMin": -15.0,
                          "esm1bMax": -5.0,
                          "sort": "esm1b",
                          "order": "asc"
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Show all variants (including potential)",
                                            summary = "Include both known and potential/predicted variants",
                                            value = """
                        {
                          "searchTerms": [{"value": "BRCA1", "type": "gene"}],
                          "known": false
                        }
                        """
                                    )
                            }
                    )
            )
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingJson(@Valid @RequestBody MappingRequest request) {
        return handleRequest(request);
    }

    /**
     * POST endpoint accepting form data.
     * Same functionality as JSON endpoint but accepts form-encoded data.
     */
   /* @Operation(summary = POST_SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingForm(@Valid @RequestBody @ModelAttribute MappingRequest request) {
        return handleRequest(request);
    }*/

    /**
     * Common request handler for POST endpoints.
     * Validates request structure, resolves search term types, and normalizes values.
     */
    public ResponseEntity<?> handleRequest(MappingRequest request) {
        // Validate and process using shared validator
        MappingRequestValidator.ValidationResult validation = requestValidator.validateAndProcess(request);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(validation.getErrorMessage());
        }

        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }
}
