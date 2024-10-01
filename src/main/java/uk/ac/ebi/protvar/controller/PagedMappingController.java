package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ebi.protvar.cache.InputCache;
import uk.ac.ebi.protvar.model.response.IDResponse;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.PagedMappingService;
import static uk.ac.ebi.protvar.config.PagedMapping.*;

@Tag(name = "Coordinate Mapping")
@RestController
@CrossOrigin
@AllArgsConstructor
public class PagedMappingController {
    public final static String PAGE_DESC = "The page number to retrieve.";
    public final static String PAGE_SIZE_DESC = "The number of results per page. Minimum 10, maximum 1000. Uses default value if not within this range.";

    // CacheMgr
    // PREFIX-uuid:value
    // "TEXT-uuid":inputText
    // "FILE-uuid":fileName
    // "PROT-acc":protein?
    // "PDB-structid":json?
    // also check which methods can be
    // annotated with @Cacheable
    // e.g. getPage(uuid, page) <- avoids re-splitting input
    // etc.

    private InputCache inputCache;
    private PagedMappingService pagedMappingService;

    @Operation(
            summary = "Submit either a text input or a file for processing",
            description = "This endpoint allows you to submit a text input or a file for processing. The response will contain a unique ID for the submitted input, " +
                    "which can be used to retrieve the results later. If both text and file inputs are provided, the file will be prioritised and processed."
    )
    @ApiResponse(
            description = "`PagedMappingResponse` by default containing the first page of results. If the `idOnly` parameter is specified, " +
                    "an `IDResponse` will be returned containing a generated ID, which can be used to retrieve results from the `/mapping/result/{id}` endpoint."
    )
    @PostMapping(
            value = "/mapping/input",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.TEXT_PLAIN_VALUE }
    )
    public ResponseEntity<?> postInput(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The text content to be processed.",
                    content = @Content(
                            examples = @ExampleObject(value = "19 1010539 G C\nP80404 Gln56Arg\nrs1042779")
                    )
            )
            @RequestBody(required = false) String text,
            @Parameter(description = "The file to be processed. If both text and file are specified, the file will take precedence.")
            @RequestParam(required = false) MultipartFile file,
            @Parameter(description = CoordinateMappingController.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly,
            @Parameter(description = "If set to true, the response will contain only the generated ID, which can be used to retrieve the results later.")
            @RequestParam(required = false, defaultValue = "false") boolean idOnly
            ) {
        String id = null;
        if (file != null) {
            id = inputCache.cache(file);
        } else if (text != null) {
            id = inputCache.cache(text);
        }
        if (id != null) {
            if (idOnly)
                return new ResponseEntity<>(new IDResponse(id), HttpStatus.OK);
            else
                return getPagedResponse(id, DEFAULT_PAGE, DEFAULT_PAGE_SIZE, assembly);
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<?> getPagedResponse(String id, int page, int pageSize, String assembly) {
        PagedMappingResponse response = pagedMappingService.getInputResult(id, page, pageSize, assembly);
        if (response != null)
            return new ResponseEntity<>(response, HttpStatus.OK);
        else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Operation(
            summary = "Return mappings for a given input ID",
            description = "Retrieve paginated genomic-protein mappings for a specific input ID. This endpoint returns the results in JSON format. " +
                    "You can specify the page number and page size for pagination. Additionally, you can specify the assembly version."
    )
    @GetMapping(value = "/mapping/input/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getResult(
            @Parameter(description = "The unique ID of the input to retrieve mappings for.", example = "id")
            @PathVariable("id") String id,
            @Parameter(description = PAGE_DESC, example = PAGE)
            @RequestParam(value = "page", defaultValue = PAGE, required = false) int page,
            @Parameter(description = PAGE_SIZE_DESC, example = PAGE_SIZE)
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) int pageSize,
            @Parameter(description = CoordinateMappingController.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {

        if (page < 1)
            page = DEFAULT_PAGE;
        if (pageSize < PAGE_SIZE_MIN || pageSize > PAGE_SIZE_MAX)
            pageSize = DEFAULT_PAGE_SIZE;

        return getPagedResponse(id, page, pageSize, assembly);
    }

    @Operation(
            summary = "Extends result expiry by another 30 days"
    )
    @GetMapping(value = "/mapping/input/{id}/renew")
    public ResponseEntity<?> extendExpiry(@PathVariable("id") String id) {
        if (inputCache.extend(id))
            return new ResponseEntity<>(HttpStatus.OK);
        return ResponseEntity.notFound().build();
    }

    @Operation(
            summary = "Retrieve all mappings for the provided UniProt accession",
            description = "Fetch paginated genomic-protein mappings for a specified UniProt accession. This endpoint returns the results in JSON format. " +
                    "You can specify the page number and the number of results per page for pagination."
    )
    @GetMapping(value = "/mapping/accession/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> getResultByAccession(
            @Parameter(description = "The UniProt accession to retrieve mappings for.", example = "Q9UHP9")
            @PathVariable("id") String accession,
            @Parameter(description = PAGE_DESC, example = PAGE)
            @RequestParam(value = "page", defaultValue = PAGE, required = false) int page,
            @Parameter(description = PAGE_SIZE_DESC, example = PAGE_SIZE)
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) int pageSize) {

        if (page < 1)
            page = DEFAULT_PAGE;
        if (pageSize < PAGE_SIZE_MIN || pageSize > PAGE_SIZE_MAX)
            pageSize = DEFAULT_PAGE_SIZE;

        if (accession == null || accession.trim().isEmpty())
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        accession = accession.trim().toUpperCase();

        PagedMappingResponse response = pagedMappingService.getMappingByAccession(accession.toUpperCase(), page, pageSize);
        if (response != null)
            response.setId(accession);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
