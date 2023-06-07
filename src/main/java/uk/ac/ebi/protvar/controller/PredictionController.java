package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.data.ConservScore;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;
import uk.ac.ebi.protvar.utils.AminoAcid;

import javax.servlet.ServletContext;
import java.util.List;

@Tag(name = "Predictions", description = "Pockets, foldx and interactions.")
@RestController
@CrossOrigin
@AllArgsConstructor
public class PredictionController {
    @Autowired
    private ServletContext context;

    private ProtVarDataRepo protVarDataRepo;

    /**
     * Pocket predictions based on the AF-DB monomeric structures using autosite.
     *
     * @param accession UniProt accession
     * @param resid     Residue
     * @return <code>Pocket</code> information on accession
     */
    @Operation(summary = "- by accession and residue")
    @GetMapping(value = "/pocket/{accession}/{resid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pocket>> getPocketsByAccAndResid(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer resid) {

        List<Pocket> pockets = protVarDataRepo.getPockets(accession, resid);
        return new ResponseEntity<>(pockets, HttpStatus.OK);
    }

    /**
     * Foldx predictions based on AFDB.
     *
     * @param accession UniProt accession
     * @param position  Amino acid position
     * @param variantAA Optional, 1- or 3-letter symbol for variant amino acid
     * @return <code>Foldx</code> information on accession
     */
    @Operation(summary = "- by accession and position")
    @GetMapping(value = "/foldx/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Foldx>> getFoldxsByAccAndPos(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer position,
            @Parameter(example = "R") @RequestParam(required = false) String variantAA) {
        List<Foldx> foldxs = protVarDataRepo.getFoldxs(accession, position, AminoAcid.oneLetter(variantAA));
        return new ResponseEntity<>(foldxs, HttpStatus.OK);
    }


    /**
     * Predicted protein-protein interactions with strength/confidence of interaction.
     *
     * @param accession UniProt accession
     * @param resid     Residue
     * @return <code>Interaction</code> information on accession
     */
    @Operation(summary = "- by accession and residue")
    @GetMapping(value = "/interaction/{accession}/{resid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Interaction>> getInteractionsByAccAndResid(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer resid) {

        List<Interaction> interactions = protVarDataRepo.getInteractions(accession, resid);
        interactions.forEach(i -> i.setPdbModel(context.getContextPath() + i.getPdbModel()));
        return new ResponseEntity<>(interactions, HttpStatus.OK);
    }

    /**
     * Trimmed pdb model for two interacting proteins.
     *
     * @param a UniProt accession
     * @param b UniProt accession
     * @return Trimmed pdb model
     */
    @Operation(summary = "- by accession pair")
    @GetMapping(value = "/interaction/{a}/{b}/model", produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody String getInteractionModel(
            @Parameter(example = "Q9NUW8") @PathVariable String a,
            @Parameter(example = "Q9UBZ4") @PathVariable String b) {
        String model = protVarDataRepo.getInteractionModel(a, b);
        return model;
    }

    /**
     * Get conservation score.
     *
     * @param accession UniProt accession
     * @param position  Amino acid position
     * @return <code>ConservScore</code> information on accession
     */
    @Operation(summary = "- by accession and position")
    @GetMapping(value = "/conserv/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ConservScore>> getConservScoresByAccAndPos(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer position) {

        List<ConservScore> scores = protVarDataRepo.getConservScores(accession, position);
        return new ResponseEntity<>(scores, HttpStatus.OK);
    }
}