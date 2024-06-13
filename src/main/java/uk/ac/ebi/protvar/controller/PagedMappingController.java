package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ebi.protvar.model.response.IDResponse;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.PagedMappingService;
import static uk.ac.ebi.protvar.config.PagedMapping.*;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Tag(name = "Coordinate Mapping")
@RestController
@CrossOrigin
@AllArgsConstructor
public class PagedMappingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PagedMappingController.class);
    private RedisTemplate redisTemplate;
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

    private PagedMappingService pagedMappingService;

    private String generateChecksum(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            String checksum = new BigInteger(1, hash).toString(16);
            return checksum;
        } catch (Exception e) {
            return null;
        }
    }

    private void cacheInput(String id, String input) {
        if (!redisTemplate.hasKey(id)) {
            redisTemplate.opsForValue().set(id, input);
            redisTemplate.expireAt(id, Instant.now().plus(RESULT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
        }
    }

    // input
    // - text         - PagedResponse if size>10?
    // - file         - PagedResponse
    // - singleLine   - Response?
    final static String ASSEMBLY_DESC = "Specify the human genome assembly version. Accepted values are: GRCh38/h38/38, GRCh37/h37/37 or AUTO (default, auto-detects the version).";

    // INPUT: plain text or file input
    // OUTPUT: PagedMappingResponse (with first page results) or IDResponse (just the result ID)
    @Operation(summary = "Submit either a text input or a file for processing. The endpoint returns a unique result ID for the processed input. " +
            "If both text and file inputs are provided, the file will be prioritised and processed.")
    @ApiResponse(description = "`PagedMappingResponse` by default or `IDResponse` if idOnly parameter is set true, which can be used to retrieve the results later.")
    @PostMapping(value="/mapping/submit",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.TEXT_PLAIN_VALUE })
    public ResponseEntity<?> submit(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
            @ExampleObject(value = "19 1010539 G C\nP80404 Gln56Arg\nrs1042779"))},
                    description = "The text content to be processed.")
            @RequestBody(required = false) String text,
            @Parameter(description = "The file to be processed. If both text and file are specified, the file will take precedence.")
            @RequestParam(required = false) MultipartFile file,
            @Parameter(description = ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly,
            @Parameter(description = "Result id only")
            @RequestParam(required = false, defaultValue = "false") boolean idOnly
            ) {
        String id = null;
        if (file != null) {
            try {
                byte[] b = file.getBytes();
                id = generateChecksum(b);
                text = new String(b);
                cacheInput(id, text);
            } catch (IOException ex) {
                // will default to BAD_REQUEST
                LOGGER.error("Submitted file error", ex);
            }
        } else if (text != null) {
            id = generateChecksum(text.getBytes());
            cacheInput(id, text);
        }
        if (id != null) {
            if (idOnly)
                return new ResponseEntity<>(new IDResponse(id), HttpStatus.OK);
            else if (text != null)
                return new ResponseEntity<>(pagedMappingService.newInput(id, text, assembly), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @Operation(summary = "Return mappings for input `id` (WORK IN PROGRESS)")
    @GetMapping(value = "/mapping/result/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> getInputResult(
            @Parameter(example = "id") @PathVariable("id") String id,
            @RequestParam(value = "page", defaultValue = PAGE, required = false) int page,
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) int pageSize,
            @Parameter(description = ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {

        if (redisTemplate.hasKey(id)) {
            //System.out.println(Arrays.toString(redisTemplate.keys(uuid).toArray()));

            //if sort or filter enabled
            //   process input as a whole, then paginate
            //otherwise
            //   process section of input by page no

            // input processing steps:
            // a. group input by type
            //      1. genomic -> no additional step
            //      2. protein/cDNA/ID -> get genomic coordinates
            // b. gen coords list w/ alternate var (w/
            //      - scores?
            //      - features(annotations)?
            //      - pdbe/AF structures?
            //      to enable sort)

            String originalInput = redisTemplate.opsForValue().get(id).toString();
            return new ResponseEntity<>(pagedMappingService.getInputResult(id, originalInput, page, pageSize, assembly), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Retrieve all mappings for the provided UniProt accession (WORK IN PROGRESS)")
    @GetMapping(value = "/mappings/{accession}")
    public ResponseEntity<PagedMappingResponse> mappingsAccession(
            @Parameter(example = "Q9UHP9") @PathVariable("accession") String accession,
            @RequestParam(value = "page", defaultValue = PAGE, required = false) int page,
            @RequestParam(value = "pageSize", defaultValue = PAGE_SIZE, required = false) int pageSize) {
        PagedMappingResponse response = pagedMappingService.getMappingByAccession(accession, page, pageSize);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
