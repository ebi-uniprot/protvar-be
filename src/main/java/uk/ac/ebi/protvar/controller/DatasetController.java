package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.protvar.repo.DatasetRepo;
import uk.ac.ebi.protvar.repo.DatasetRepo.Dataset;

import java.util.List;

/**
 * Ready-made datasets anyone can open and filter like their own upload: each is an upload whose id
 * ({@code resultId}) works with POST /mapping and /download.
 */
@Tag(name = "Datasets")
@RestController
@CrossOrigin
@RequestMapping("/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetRepo datasetRepo;

    @Operation(summary = "Ready-made datasets", description = "Each with its title, source version, size and the "
            + "upload id (resultId) to open it with.")
    @GetMapping
    public List<Dataset> datasets() {
        return datasetRepo.list();
    }

    @Operation(summary = "One ready-made dataset, by name (e.g. humsavar)")
    @GetMapping("/{name}")
    public ResponseEntity<Dataset> dataset(@PathVariable String name) {
        return datasetRepo.list().stream().filter(d -> d.name().equals(name)).findFirst()
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
