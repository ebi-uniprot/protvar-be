package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.protvar.repo.AlleleFreqRepo;

import java.util.List;

@Tag(name = "Allele Frequency")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class AlleleFreqController {

    private final AlleleFreqRepo alleleFreqRepo;

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
    @PostMapping(value = "/allele-freq", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAlleleFreqs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            examples = @ExampleObject(value = "[[\"1\", 56933412], [\"2\", 233760498]]")
                    )
            )
            @RequestBody List<List<Object>> chrPosList) {
        if (chrPosList == null || chrPosList.isEmpty()) {
            return ResponseEntity.badRequest().body("Empty input");
        }

        int size = chrPosList.size();
        String[] chromosomes = new String[size];
        Integer[] positions = new Integer[size];

        for (int i = 0; i < size; i++) {
            List<Object> entry = chrPosList.get(i);
            if (entry.size() != 2) {
                return ResponseEntity.badRequest().body("Each input must have exactly two elements: [chr, pos]");
            }

            chromosomes[i] = entry.get(0).toString();
            positions[i] = Integer.parseInt(entry.get(1).toString());
        }

        List<AlleleFreq> alleleFreqs = alleleFreqRepo.getAlleleFreqs(chromosomes, positions);
        return ResponseEntity.ok(alleleFreqs);
    }
}
