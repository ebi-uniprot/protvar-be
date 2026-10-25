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

    @Operation(summary = "Current release statistics", description = "One entry per figure: label, value, source and version.")
    @GetMapping
    public ResponseEntity<List<ReleaseStat>> getStats() {
        return ResponseEntity.ok(statsService.getStats());
    }
}
