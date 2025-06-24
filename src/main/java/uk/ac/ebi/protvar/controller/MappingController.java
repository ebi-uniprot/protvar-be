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
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.MappingService;
import uk.ac.ebi.protvar.types.InputType;
import uk.ac.ebi.protvar.utils.InputTypeResolver;

import static uk.ac.ebi.protvar.constants.PageUtils.*;

@Tag(name = "Coordinate Mapping")
@RestController
@RequestMapping("/mapping")
@CrossOrigin
@RequiredArgsConstructor
public class MappingController {
    private final static String SUMMARY = """
            Retrieve mappings for the specified input and type (UniProt accession, gene symbol, Ensembl, PDB or RefSeq ID).
            If `type` is not specified, it will be inferred automatically.
            """;
    public final static String PAGE_DESC = "The page number to retrieve.";
    public final static String PAGE_SIZE_DESC = "The number of results per page. Minimum 10, maximum 1000. Uses default value if not within this range.";
    private final MappingService mappingService;


    @Operation(
        summary = "Retrieve mappings for a single variant input - used for direct query. Unpaged response."
    )
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MappingResponse> getSingleVariantMapping(
            @Parameter(description = "Single variant query in a supported format.", example = "19-1010539-G-C")
            @RequestParam String input,
            @RequestParam(required = false) String assembly) {
        /*
                MappingRequest request = MappingRequest.builder()
                .inputs(inputs)
                .function(function)
                .population(population)
                .structure(structure)
                .assembly(assembly)
                .build();
         */
        // todo:
        // create a MappingRequest, with provided input, and inputType=SINGLE_VARIANT?
        return new ResponseEntity<>(mappingService.get(input, assembly), HttpStatus.OK);
    }

    @Operation(
            summary = "Retrieve mappings for a given input ID"
    )
    // TODO use a simple or Request DTO here
    @GetMapping(value = "/{inputId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getInputIdMapping(
            @Parameter(description = "The unique ID of the input to retrieve mappings for.", example = "id")
            @PathVariable("inputId") String inputId,
            @Parameter(description = PAGE_DESC, example = PAGE)
            @RequestParam(value = "page", defaultValue = PAGE, required = false) int page,
            @Parameter(description = PAGE_SIZE_DESC, example = PAGE_SIZE)
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) int pageSize,
            @Parameter(description = InputUploadController.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {

        if (page < 1)
            page = DEFAULT_PAGE;
        if (pageSize < PAGE_SIZE_MIN || pageSize > PAGE_SIZE_MAX)
            pageSize = DEFAULT_PAGE_SIZE;

        PagedMappingResponse response = mappingService.getInputIdMapping(inputId, page, pageSize, assembly);
        if (response != null)
            return new ResponseEntity<>(response, HttpStatus.OK);
        else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Operation(summary = SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingGet(@Valid @RequestBody MappingRequest request) {
        return handleRequest(request);
    }

    @Operation(summary = SUMMARY)
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mappingPost(@Valid @RequestBody @ModelAttribute MappingRequest request) {
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
                (resolved != InputType.PDB || resolved != InputType.INPUT_ID)) {
            request.setInput(request.getInput().toUpperCase());
        }

        return new ResponseEntity<>(mappingService.get(request), HttpStatus.OK);
    }
}
