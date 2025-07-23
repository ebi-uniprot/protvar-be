package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.MappingService;
import uk.ac.ebi.protvar.types.InputType;
import uk.ac.ebi.protvar.utils.InputTypeResolver;

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
    private final static String SUMMARY = """
            Retrieve mappings for the specified input and type (UniProt accession, gene symbol, Ensembl, PDB or RefSeq ID).
            If `type` is not specified, the system will try to infer it automatically.
            """;
    private final Validator validator;
    private final MappingService mappingService;

    /**
     * Example URLs
     * 1. With both parameters:
     * /mapping?input=19-1010539-G-C&assembly=GRCh38
     * 2. With only the required parameter (uses default assembly = AUTO):
     * /mapping?input=19-1010539-G-C
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
    public ResponseEntity<PagedMappingResponse> singleInput(
            @Parameter(description = "Single variant query in a supported format.", example = "19-1010539-G-C")
            @RequestParam String input,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        MappingRequest request = MappingRequest.builder()
                .input(input)
                .type(InputType.SINGLE_VARIANT)
                .assembly(assembly)
                .page(1)
                .pageSize(1)
                .build();
        PagedMappingResponse response = mappingService.get(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(summary = "Retrieve mappings for a given input ID")
    @GetMapping(value = "/{inputId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getInputIdMapping(
            @Parameter(description = "The unique ID of the input to retrieve mappings for.", example = "id")
            @PathVariable("inputId") String inputId,
            @Parameter(description = MappingRequest.PAGE_DESC, example = PAGE)
            @RequestParam(value = "page", defaultValue = PAGE, required = false) Integer page,
            @Parameter(description = MappingRequest.PAGE_SIZE_DESC, example = PAGE_SIZE)
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) Integer pageSize,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {

        // we may not need to do these checks
        if (page < 1) page = DEFAULT_PAGE;
        if (pageSize < PAGE_SIZE_MIN || pageSize > PAGE_SIZE_MAX) pageSize = DEFAULT_PAGE_SIZE;

        MappingRequest request = MappingRequest.builder()
                .input(inputId)
                .type(InputType.INPUT_ID)
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

    @Operation(summary = SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingJson(@Valid @RequestBody MappingRequest request) {
        return handleRequest(request);
    }

    @Operation(summary = SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingForm(@Valid @RequestBody @ModelAttribute MappingRequest request) {
        return handleRequest(request);
    }

    public ResponseEntity<?> handleRequest(MappingRequest request) {
        InputType type = request.getType(); // user-provided, may be made optional
        InputType resolved = InputTypeResolver.resolve(request.getInput());

        if (type != null && !type.equals(resolved)) {
            return ResponseEntity.badRequest().body(
                    String.format("Input type '%s' does not match resolved type '%s'.", type, resolved)
            );
        }
        if (resolved == null) {
            return ResponseEntity.badRequest().body("Unable to resolve input type from provided input.");
        }

        request.setType(resolved);

        if (request.getInput() != null &&
                (resolved != InputType.PDB && resolved != InputType.INPUT_ID)) { // todo: move normalizing case in SQL query for consistency
            request.setInput(request.getInput().toUpperCase());
        }

        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }
}
