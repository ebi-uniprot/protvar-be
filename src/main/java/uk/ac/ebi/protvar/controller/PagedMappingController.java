package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.PagedMappingService;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Tag(name = "Paged Mapping")
@RestController
@CrossOrigin
@AllArgsConstructor
public class PagedMappingController {
    private RedisTemplate redisTemplate;
    // CacheMgr
    // PREFIX-uuid:value
    // "TEXT-uuid":inputText
    // "FILE-uuid":fileName
    // "PROT-acc":protein?
    // "PDB-structid":json?
    // also check which methods can be
    // annotated with @Cacheable
    // e.g. getPage(uuid, pageNo) <- avoids re-splitting input
    // etc.

    private PagedMappingService pagedMappingService;

    // INPUT: plain text or file
    // OUTPUT: resultId="uuid", firstX results

    String generateChecksum(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            String checksum = new BigInteger(1, hash).toString(16);
            return checksum;
        } catch (Exception e) {
            return null;
        }
    }

    // input
    // - text         - PagedResponse if size>10?
    // - file         - PagedResponse
    // - singleLine   - Response

    @Operation(summary = "Submit variant input (WORK IN PROGRESS)")
    @PostMapping(value="/mappings/textInput", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<PagedMappingResponse> postInput(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
            @ExampleObject(value = "19 1010539 G C\nP80404 Gln56Arg\nrs1042779"))})
            @RequestBody String requestBody) {
        // generate checksum
        // FILE: Files.readAllBytes(Paths.get(filePath));
        String id = generateChecksum(requestBody.getBytes());
        // TODO handle null
        // store id:input
        if (!redisTemplate.hasKey(id)) {
            redisTemplate.opsForValue().set(id, requestBody);
            redisTemplate.expireAt(id, Instant.now().plus(14, ChronoUnit.DAYS));
        }

        return new ResponseEntity<>(pagedMappingService.newInput(id, requestBody), HttpStatus.OK);
    }

    @Operation(summary = "Return mappings for input `id` (WORK IN PROGRESS)")
    @GetMapping(value = "/input/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedMappingResponse> getInputResult(
            @Parameter(example = "id") @PathVariable("id") String id,
            @RequestParam(value = "pageNo", defaultValue = "1", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize) {

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
            return new ResponseEntity<>(pagedMappingService.getInputResult(id, originalInput, pageNo, pageSize), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
