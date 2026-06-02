package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.data.Stats;
import uk.ac.ebi.protvar.service.StatsService;

import java.util.List;

@Tag(name = "Stats")
@RestController
@CrossOrigin
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @Value("${uniprot.release}")
    private String defaultRelease;

    @GetMapping("/{type}/{key}")
    public ResponseEntity<?> getLatestStat(
            @PathVariable String type,
            @PathVariable String key,
            @RequestParam(value = "release", required = false) String release) {
        String effectiveRelease = (release != null) ? release : defaultRelease;
        return statsService.getLatestStat(effectiveRelease, type, key)
                .map(stat -> ResponseEntity.ok(stat.getValue()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/latest")
    public ResponseEntity<List<Stats>> getAllLatestStats(
            @RequestParam(value = "release", required = false) String release) {
        String effectiveRelease = (release != null) ? release : defaultRelease;
        return ResponseEntity.ok(statsService.getAllLatestStats(effectiveRelease));
    }
}
