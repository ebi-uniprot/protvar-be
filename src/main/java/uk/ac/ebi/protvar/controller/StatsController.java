package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.data.Stats;
import uk.ac.ebi.protvar.service.StatsService;

import java.util.List;

@Tag(name = "Stats")
@RestController
@CrossOrigin
@AllArgsConstructor
@RequestMapping("/stats")
public class StatsController {
    @Autowired
    private StatsService statsService;

    @GetMapping("/{importType}/{keyName}")
    public ResponseEntity<?> getLatestStat(@PathVariable String importType, @PathVariable String keyName) {
        return statsService.getLatestStat(importType, keyName)
                .map(stat -> ResponseEntity.ok(stat.getValue()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/latest")
    public ResponseEntity<List<Stats>> getAllLatestStats() {
        return ResponseEntity.ok(statsService.getAllLatestStats());
    }

    @GetMapping("/core")
    public ResponseEntity<List<Stats>> getCoreStats() {
        List<Stats> coreStats = statsService.getCoreStats();
        return ResponseEntity.ok(coreStats);
    }
}
