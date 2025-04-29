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

import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.protvar.fetcher.PDBeFetcher;
import uk.ac.ebi.protvar.fetcher.ProteinsFetcher;
import uk.ac.ebi.protvar.fetcher.VariantFetcher;
import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.repo.FoldxRepo;
import uk.ac.ebi.protvar.repo.InteractionRepo;
import uk.ac.ebi.protvar.repo.PocketRepo;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.uniprot.domain.variation.Variant;

@Tag(name = "Annotation")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class AnnotationController {


  private final ProteinsFetcher proteinsFetcher;
  private final VariantFetcher variantFetcher;

  private final PDBeFetcher pdbeFetcher;

  private final PocketRepo pocketRepo;

  private final InteractionRepo interactionRepo;

  private final FoldxRepo foldxRepo;

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
    FunctionalInfo functionalInfo = proteinsFetcher.fetch(accession, position, AminoAcid.oneLetter(variantAA));
    // Add novel predictions
    functionalInfo.setPockets(pocketRepo.getPockets(accession, position));
    functionalInfo.setInteractions(interactionRepo.getInteractions(accession, position));
    functionalInfo.setFoldxs(foldxRepo.getFoldxs(accession, position, variantAA));
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
   * @return List of <code>PDBeStructureResidue</code> Mappings from UniProt position to position in all relevant PDB structures
   */
  @Operation(summary = "Returns the position in PDB structures for the input variant")
  @GetMapping(value = "/structure/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<PDBeStructureResidue>> getStructure(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position) {
    List<PDBeStructureResidue> object = pdbeFetcher.fetch(accession, position);
    return new ResponseEntity<>(object, HttpStatus.OK);
  }
}
