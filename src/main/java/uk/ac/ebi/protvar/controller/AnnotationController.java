package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.mapper.AnnotationData;
import uk.ac.ebi.protvar.mapper.AnnotationFetcher;
import uk.ac.ebi.protvar.mapper.FunctionalInfoEnricher;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.response.StructureResidue;
import uk.ac.ebi.protvar.service.FunctionalAnnService;
import uk.ac.ebi.protvar.service.StructureService;
import uk.ac.ebi.protvar.types.AminoAcid;
import java.util.List;

@Tag(name = "Annotation")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class AnnotationController {


  private final FunctionalAnnService functionalAnnService; // todo: rename to FunctionalInfoService?
  private final AnnotationFetcher annotationFetcher;
  private final FunctionalInfoEnricher functionalInfoEnricher;
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

    FunctionalInfo functionalInfo = functionalAnnService.get(accession, position);
    if (functionalInfo != null) {
      if (variantAA != null) {
        try {
          variantAA = AminoAcid.oneLetter(variantAA);
        } catch (IllegalArgumentException e) {
          return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
          //log.warn("Invalid variant amino acid '{}'", variantAA);
          //variantAA = null; // or throw a 400 from controller if needed
        }
      }

      AnnotationData annData = annotationFetcher.getAPIFunctionalData(accession, position, variantAA);
      functionalInfoEnricher.enrich(functionalInfo, annData, variantAA);
      return new ResponseEntity<>(functionalInfo, HttpStatus.OK);
    }

    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
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
    @Parameter(example = "493") @PathVariable("position") int position,
    @Parameter(example = "14-89993420-A-C") @RequestParam(required = false) String genomicVariant) {

    GenomicInput genomicInput = InputParser.parseValidGenomicInput(genomicVariant);
    String chromosome = null;
    Integer genomicPosition = null;
    String altBase = null;

    if (genomicInput != null) {
      chromosome = genomicInput.getChr();
      genomicPosition = genomicInput.getPos();
      //altBase = genomicInput.getAlt(); // commented to return all bases
    }

    AnnotationData annData = annotationFetcher.getAPIPopulationData(accession, position, chromosome, genomicPosition);

    if (annData == null)
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);

    PopulationObservation populationObservation = annData.get(accession, position, chromosome, genomicPosition, altBase);

    return new ResponseEntity<>(populationObservation, HttpStatus.OK);
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
