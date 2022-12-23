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
import uk.ac.ebi.protvar.model.response.Foldx;
import uk.ac.ebi.protvar.model.response.Interaction;
import uk.ac.ebi.protvar.model.response.Interface;
import uk.ac.ebi.protvar.model.response.Pocket;
import uk.ac.ebi.protvar.repo.ProtVarDataRepo;

import javax.servlet.ServletContext;
import java.util.List;

@Tag(name = "Predictions", description = "Pockets and predicted interfaces and foldx predictions. Protein interactions " +
        "with confidence/strength and trimmed pdb model.")
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
     * @return <code>Pocket</code> information on accession
     */
    @Operation(summary = "- by accession")
    @GetMapping(value = "/pocket/{accession}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pocket>> getPocketsByAcc(
            @Parameter(example = "Q9NUW8") @PathVariable String accession) {
        List<Pocket> pockets = protVarDataRepo.getPockets(accession);
        return new ResponseEntity<>(pockets, HttpStatus.OK);
    }

    /**
     * Pocket predictions based on the AF-DB monomeric structures using autosite.
     *
     * @param accession UniProt accession
     * @param resid     Amino acid position
     * @return <code>Pocket</code> information on accession
     */
    @Operation(summary = "- by accession and resid")
    @GetMapping(value = "/pocket/{accession}/{resid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pocket>> getPocketsByAccAndResid(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer resid) {

        List<Pocket> pockets = protVarDataRepo.getPockets(accession, resid);
        return new ResponseEntity<>(pockets, HttpStatus.OK);
    }

    /**
     * Predicted interfaces.
     *
     * @param accession UniProt accession
     * @param residue     Amino acid position
     * @return <code>Interface</code> information on accession
     */
    @Operation(summary = "- by accession and residue")
    @GetMapping(value = "/interface/{accession}/{residue}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Interface>> getInterfacesByAccAndResid(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer residue) {

        List<Interface> interfaces = protVarDataRepo.getInterfaces(accession, residue);
        return new ResponseEntity<>(interfaces, HttpStatus.OK);
    }

    /**
     * Foldx predictions based on AFDB.
     *
     * @param accession UniProt accession
     * @param position     Amino acid position
     * @return <code>Foldx</code> information on accession
     */
    @Operation(summary = "- by accession and position")
    @GetMapping(value = "/foldx/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Foldx>> getFoldxsByAccAndPos(
            @Parameter(example = "Q9NUW8") @PathVariable String accession,
            @Parameter(example = "493") @PathVariable Integer position) {

        List<Foldx> interfaces = protVarDataRepo.getFoldxs(accession, position);
        return new ResponseEntity<>(interfaces, HttpStatus.OK);
    }

    /**
     * Protein interactions confidence/strength.
     *
     * @param a UniProt accession
     * @param b UniProt accession
     * @return <code>Interaction</code> information on accession
     */
    @Operation(summary = "- by accession pair")
    @GetMapping(value = "/interaction/{a}/{b}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Interaction> getInteraction(
            @Parameter(example = "Q9NUW8") @PathVariable String a,
            @Parameter(example = "Q9UBZ4") @PathVariable String b) {

        Interaction interaction = protVarDataRepo.getPairInteraction(a, b);
        if (interaction != null && interaction.getModelUrl() != null) {
            interaction.setModelUrl(context.getContextPath() + interaction.getModelUrl());
        }
        return new ResponseEntity<>(interaction, HttpStatus.OK);
    }
    /**
     * Protein interactions trimmed pdb model.
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
        String model = protVarDataRepo.getPairInteractionModel(a, b);
        return model;
    }
}