package uk.ac.ebi.protvar.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.service.APIService;
import uk.ac.ebi.protvar.utils.AminoAcid;

@Tag(name = "Annotation")
@RestController
@CrossOrigin
@AllArgsConstructor
public class AnnotationController {
  private APIService apiService;

  /**
   * @param accession UniProt accession
   * @param position  Amino acid position
   * @param variantAA Optional, 1- or 3-letter symbol for variant amino acid
   * @return <code>Protein</code> information on accession
   */
  @Operation(summary = "Retrieve functional annotations for an amino acid")
  @GetMapping(value = "/function/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Protein> getFunction(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position,
    @Parameter(example = "R") @RequestParam(required = false) String variantAA) {
    Protein protein = apiService.getProtein(accession, position, AminoAcid.oneLetter(variantAA));
    return new ResponseEntity<>(protein, HttpStatus.OK);
  }

  /**
   * @param accession Uniprot accession
   * @param position  Amino acid position
   * @return <code>PopulationObservation</code> List of variants co-located at the same residue as the input.
   */
  @Operation(summary = "Retrieve other variants co-located at the same amino acid position")
  @GetMapping(value = "/population/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PopulationObservation> getPopulationObservation(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position) {
    PopulationObservation variations = apiService.getPopulationObservation(accession, position);
    return new ResponseEntity<>(variations, HttpStatus.OK);
  }

  /**
   * @param accession Uniprot accession
   * @param position  Amino acid position
   * @return List of <code>PDBeStructureResidue</code> Mappings from UniProt position to position in all relevant PDB structures
   */
  @Operation(summary = "Returns the position in PDB structures for the input variant")
  @GetMapping(value = "/structure/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<PDBeStructureResidue>> getStructure(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position) {
    List<PDBeStructureResidue> object = apiService.getStructure(accession, position);
    return new ResponseEntity<>(object, HttpStatus.OK);
  }
}
