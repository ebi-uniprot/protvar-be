package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.Coord;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.protvar.repo.AlleleFreqRepo;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Allele Frequency")
@RestController
@CrossOrigin
@AllArgsConstructor
public class AlleleFreqController {

    private AlleleFreqRepo alleleFreqRepo;

    /**
     * Gnomad allele frequency.
     *
     * @param chr Chromosome
     * @param pos  Genomic position
     * @param alt Optional, alternate allele
     * @return <code>List<AlleleFreq></code>
     */
    @Operation(summary = "Gnomad allele frequency",
            description = "Retrieve the allele frequency for the genomic coordinate (chromosome and position), and alternate allele (optional)")
    @GetMapping(value = "/allelefreq/{chr}/{pos}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AlleleFreq>> getAlleleFreq(
            @Parameter(example = "2") @PathVariable String chr,
            @Parameter(example = "233760498") @PathVariable Integer pos,
            @Parameter(example = "A") @RequestParam(required = false) String alt) {
        List<AlleleFreq> alleleFreqs = alleleFreqRepo.getAlleleFreq(chr, pos, alt);
        return new ResponseEntity<>(alleleFreqs, HttpStatus.OK);
    }

    @Operation(summary = "Gnomad allele frequencies - multiple inputs ",
            description = "Retrieve the allele frequencies for the list of genomic coordinates (chromosome-position pairs)")
    @PostMapping(value = "/allelefreqs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AlleleFreq>> getAlleleFreqs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            examples = @ExampleObject(value = "[\n" +
                                    "    { \"chr\": \"1\", \"pos\": 56933412 },\n" +
                                    "    { \"chr\": \"2\", \"pos\": 233760498 }\n" +
                                    "]")
                    )
            )
            @RequestBody List<Coord.Gen> chrPosList) {
        List<AlleleFreq> alleleFreqs = alleleFreqRepo.getAlleleFreqs(chrPosList.stream().map(p -> p.toObjectArray()).collect(Collectors.toList()));
        return new ResponseEntity<>(alleleFreqs, HttpStatus.OK);
    }
}
