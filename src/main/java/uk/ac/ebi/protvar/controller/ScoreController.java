package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.repo.ScoreRepo;

import java.util.List;

@Tag(name = "Score")
@RestController
@CrossOrigin
@AllArgsConstructor
public class ScoreController {
    private ScoreRepo scoreRepo;

    /**
     * Retrieve Conservation, EVE, ESM1b and AlphaMissense scores.
     *
     * @param acc UniProt accession
     * @param pos  Amino acid position
     * @param mt  Mutated type (1- or 3-letter amino acid)
     * @param name  Score name
     * @return <code>Score</code>
     */
    @Operation(summary = "Amino acid-level scores",
            description="Retrieve Conservation, EVE, ESM1b and AlphaMissense scores for accession and position. " +
                    "Mutated type (mt) is disregarded for Conservation score and optional for the other scores. " +
                    "By default, all scores are retrieved.")
    @GetMapping(value = "/score/{acc}/{pos}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Score>> getScores(
            @Parameter(example = "Q9NUW8") @PathVariable String acc,
            @Parameter(example = "493") @PathVariable Integer pos,
            @Parameter(example = "R") @RequestParam(required = false) String mt,
            @Parameter(example = "") @RequestParam(required = false) Score.Name name) {

        List<Score> scores = scoreRepo.getScores(acc, pos, mt, name);
        return new ResponseEntity<>(scores, HttpStatus.OK);
    }
}
