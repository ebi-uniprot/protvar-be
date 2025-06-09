package uk.ac.ebi.protvar.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import uk.ac.ebi.protvar.fetcher.VariantFetcher;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.service.FunctionalAnnService;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.uniprot.domain.variation.Variant;

@Tag(name = "Annotation")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class AnnotationController {


  private final FunctionalAnnService functionalAnnService;
  private final VariantFetcher variantFetcher;

  private final StructureService structureService;

  /**
   * @param accession UniProt accession
   * @param position  Amino acid position
   * @param variantAA Optional, 1- or 3-letter symbol for variant amino acid
   * @return <code>FunctionalInfo</code> on accession and position
   */
  @Operation(summary = "Retrieve functional annotations for an amino acid")
  @GetMapping(value = "/function/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FunctionalInfo> getFunction(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position,
    @Parameter(example = "R") @RequestParam(required = false) String variantAA) {
    FunctionalInfo functionalInfo = functionalAnnService.get(accession, position, AminoAcid.oneLetter(variantAA));
    if (functionalInfo == null) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    return new ResponseEntity<>(functionalInfo, HttpStatus.OK);
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
    List<Variant> variants = variantFetcher.getVariants(accession, position);
    return new ResponseEntity<>(new PopulationObservation(variants), HttpStatus.OK);
  }

  /**
   * @param accession Uniprot accession
   * @param position  Amino acid position
   * @return List of <code>StructureResidue</code> Mappings from UniProt position to position in all relevant PDB structures
   */
  @Operation(summary = "Returns the position in PDB structures for the input variant")
  @GetMapping(value = "/structure/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<StructureResidue>> getStructure(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position) {
    List<StructureResidue> object = structureService.getStr(accession, position);
    return new ResponseEntity<>(object, HttpStatus.OK);
  }
}
