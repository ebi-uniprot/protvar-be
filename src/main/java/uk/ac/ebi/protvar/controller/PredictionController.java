package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.response.PredictionInfo;
import uk.ac.ebi.protvar.repo.InteractionRepo;
import uk.ac.ebi.protvar.service.PredictionService;
import uk.ac.ebi.protvar.types.AminoAcid;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Predictions at a protein position in one call (replaces /score and the per-type
 * /prediction/foldx|pocket|interaction endpoints), plus the predicted complex model for an interface.
 */
@Tag(name = "Prediction")
@RestController
@RequestMapping("/prediction")
@CrossOrigin
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;
    private final InteractionRepo interactionRepo;

    @Operation(summary = "Predictions at a protein position",
            description = "Residue level: conserv (conservation), pocket (predicted pockets), interface (predicted "
                    + "protein–protein interfaces). Variant level, per variant amino acid (all 19, or variantAA only): "
                    + "alphamissense, popeve, esm (ESM1b), eve, foldx (stability; the middle AlphaFold fragment, with "
                    + "numFragments), m3d (Missense3D). include= returns only the listed keys.")
    @GetMapping(value = "/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPredictions(
            @Parameter(example = "P22304") @PathVariable String accession,
            @Parameter(example = "205") @PathVariable int position,
            @Parameter(description = "Optional variant amino acid (1- or 3-letter).", example = "P")
            @RequestParam(required = false) String variantAA,
            @Parameter(description = "Keys to return (default all): conserv, pocket, interface, alphamissense, "
                    + "popeve, esm, eve, foldx, m3d", example = "foldx,alphamissense")
            @RequestParam(required = false) List<String> include) {
        String mt = null;
        if (variantAA != null && !variantAA.isBlank()) {
            mt = AminoAcid.oneLetter(variantAA);   // null when not an amino acid (it doesn't throw)
            if (mt == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown amino acid: " + variantAA));
        }
        Set<String> keys = new LinkedHashSet<>();
        if (include != null) {
            for (String k : include) {
                String key = k.trim().toLowerCase();
                if (key.isEmpty()) continue;
                if (!PredictionService.KEYS.contains(key))
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "Unknown prediction '" + k + "'; expected any of " + PredictionService.KEYS));
                keys.add(key);
            }
        }
        PredictionInfo info = predictionService.get(accession.toUpperCase(), position, mt, keys);
        return ResponseEntity.ok(info);
    }

    @Operation(summary = "Predicted complex model for an interface (PDB text)",
            description = "The AlphaFold2 model of proteins a and b together, trimmed to the interface; either order.")
    @GetMapping(value = "/interface/{a}/{b}/model", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getInterfaceModel(
            @Parameter(example = "P22304") @PathVariable String a,
            @Parameter(example = "Q9BWS9-3") @PathVariable String b) {
        String model = interactionRepo.getInteractionModel(a, b);
        return model == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(model);
    }
}
