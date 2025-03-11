package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.data.*;
import uk.ac.ebi.protvar.repo.PredictionRepo;
import uk.ac.ebi.protvar.utils.AminoAcid;

import javax.servlet.ServletContext;
import java.util.List;

@Tag(name = "Prediction")
@RestController
@CrossOrigin
@AllArgsConstructor
public class PredictionController {
    @Autowired
    private ServletContext context;

    private PredictionRepo predictionRepo;

    @Operation(summary = "Nucleotide predictions - CADD (WIP)",
            description="Retrieve CADD score for the given genomic positions.")
    @PostMapping(value = "/prediction/cadd", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getCADDScores(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
            @ExampleObject(value = "[\"19 1010539\"]"))})
            @RequestBody List<String> coordinates) { // note coords normally refer to genomic coord whereas position is
        // used more generally for e.g. genomic/protein position, UniProt or PDB position, etc.
        return new ResponseEntity<>(null, HttpStatus.OK);
    }



    /**
     * Foldx predictions based on AFDB.
     *
     * @param accession UniProt accession
     * @param position  Amino acid position
     * @param variantAA Optional, 1- or 3-letter symbol for variant amino acid
     * @return <code>Foldx</code> information on accession
     */
    @Operation(summary = "Predictions based on structure - foldx",
            description = "Retrieve foldx predictions for accession and position")
    @GetMapping(value = { "/foldx/{accession}/{position}", "/prediction/foldx/{accession}/{position}"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Foldx>> getFoldxsByAccAndPos(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer position,
            @Parameter(example = "R") @RequestParam(required = false) String variantAA) {
        List<Foldx> foldxs = predictionRepo.getFoldxs(accession, position, AminoAcid.oneLetter(variantAA));
        return new ResponseEntity<>(foldxs, HttpStatus.OK);
    }

    /**
     * Pocket predictions based on the AF-DB monomeric structures using autosite.
     *
     * @param accession UniProt accession
     * @param resid     Residue
     * @return <code>Pocket</code> information on accession
     */
    @Operation(summary = "Predictions based on structure - pocket",
            description = "Retrieves predicted pockets for accession and residue")
    @GetMapping(value = { "/pocket/{accession}/{resid}", "/prediction/pocket/{accession}/{resid}"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pocket>> getPocketsByAccAndResid(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer resid) {

        List<Pocket> pockets = predictionRepo.getPockets(accession, resid);
        return new ResponseEntity<>(pockets, HttpStatus.OK);
    }

    /**
     * Predicted protein-protein interactions with strength/confidence of interaction.
     *
     * @param accession UniProt accession
     * @param resid     Residue
     * @return <code>Interaction</code> information on accession
     */
    @Operation(summary = "Predictions based on structure - interaction",
            description = "Retrieve predicted interacting structures for accession and residue")
    @GetMapping(value = { "/interaction/{accession}/{resid}", "/prediction/interaction/{accession}/{resid}" }, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Interaction>> getInteractionsByAccAndResid(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer resid) {

        List<Interaction> interactions = predictionRepo.getInteractions(accession, resid);
        interactions.forEach(i -> i.setPdbModel(context.getContextPath() + i.getPdbModel()));
        return new ResponseEntity<>(interactions, HttpStatus.OK);
    }

    /**
     * Trimmed pdb model for two interacting proteins.
     *
     * @param a UniProt accession
     * @param b UniProt accession
     * @return pdb model
     */
    @Operation(summary = "Predictions based on structure - interaction model",
            description = "Retrieve model of two interacting protein")
    @GetMapping(value = { "/interaction/{a}/{b}/model", "/prediction/interaction/{a}/{b}/model" }, produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody String getInteractionModel(
            @Parameter(example = "Q9NUW8") @PathVariable String a,
            @Parameter(example = "Q9UBZ4") @PathVariable String b) {
        String model = predictionRepo.getInteractionModel(a, b);
        return model;
    }

}