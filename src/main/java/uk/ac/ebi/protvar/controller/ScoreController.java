package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.model.score.ScoreType;
import uk.ac.ebi.protvar.repo.ScoreNewRepo;
import uk.ac.ebi.protvar.repo.ScoreRepo;

import java.util.List;

@Tag(name = "Score")
@RestController
@RequestMapping("/score")
@CrossOrigin
@RequiredArgsConstructor
public class ScoreController {
    private final ScoreNewRepo scoreRepo;

    /**
     * Retrieve Conservation, EVE, ESM1b and AlphaMissense scores.
     *
     * @param acc UniProt accession
     * @param pos  Amino acid position
     * @param mt  Mutated type (1- or 3-letter amino acid)
     * @param type  Score type
     * @return <code>Score</code>
     */
    @Operation(summary = "Scores at a protein position",
            description = "Conservation, AlphaMissense, ESM1b, popEVE and EVE. "
                    + "mt (variant amino acid) narrows variant-level scores; type returns one score only.")
    @GetMapping(value = "/{acc}/{pos}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Score>> getScores(
            @Parameter(example = "Q9NUW8") @PathVariable String acc,
            @Parameter(example = "493") @PathVariable Integer pos,
            @Parameter(example = "R") @RequestParam(required = false) String mt,
            @Parameter(example = "") @RequestParam(required = false) ScoreType type) {

        List<Score> scores = scoreRepo.getScores(acc, pos, mt, type);
        return new ResponseEntity<>(scores, HttpStatus.OK);
    }
}
