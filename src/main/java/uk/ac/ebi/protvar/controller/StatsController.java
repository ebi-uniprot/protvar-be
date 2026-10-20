package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.response.ReleaseStat;
import uk.ac.ebi.protvar.service.StatsService;

import java.util.List;

@Tag(name = "Stats")
@RestController
@CrossOrigin
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @Operation(summary = "Statistics for the current release: what ProtVar covers",
            description = "One entry per figure, with its label, description, page layout, value, source and version. "
                    + "inSync=false flags a figure counted from a table the API does not serve.")
    @GetMapping
    public ResponseEntity<List<ReleaseStat>> getStats() {
        return ResponseEntity.ok(statsService.getStats());
    }
}
