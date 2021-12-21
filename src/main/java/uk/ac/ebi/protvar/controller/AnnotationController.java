package uk.ac.ebi.protvar.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.service.APIService;

@Tag(name = "Individual Amino Acid Annotations", description = "Retrieve specific amino acid annotations\n\n\n" +
  "All three endpoints retrieve annotation data based on the residue position in the canonical isoform. " +
  "The categories mirror the annotation categories in the ProtVat website.")
@RestController
@CrossOrigin
@AllArgsConstructor
public class AnnotationController {
  private APIService service;

  /**
   * Returns functional information relevant to the input residue in the UniProt canonical isoform, the region
   * in which the variant amino acid resides and more general information about the protein. This is a wrapper
   * API based upon protein API which allows us to retrieve information
   * from a single residue. If annotations for the entire protein are required then please use the
   * <a href="https://www.ebi.ac.uk/proteins/api/doc/index.html#!/proteins/search" target="_new">Protein API</a>
   *
   * @param accession UniProt accession
   * @param position  Amino acid position
   * @return <code>Protein</code> information on accession
   */
  @Operation(summary = "- functional annotations for an amino acid")
  @GetMapping(value = "/function/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Protein> getFunction(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position) {
    Protein protein = service.getProtein(accession, position);
    return new ResponseEntity<>(protein, HttpStatus.OK);
  }

  /**
   * Returns descriptions from other databases which also contain the input variant. Additionally, data from
   * coding variants which map to the same amino acid in the UniProt canonical isoform. Contains disease
   * associations from literature for all co-located variants. This is a wrapper API based upon
   * variants API which allows us to retrieve information
   * from a single residue. If variant co-location data for the entire protein are required then please use the
   * <a href="https://www.ebi.ac.uk/proteins/api/doc/index.html#!/variation/search" target="_new">Variants API</a> directly.
   *
   * @param accession Uniprot accession
   * @param position  Amino acid position
   * @return <code>PopulationObservation</code> List of varianst co-located at the same residue as the input.
   * For now genomicColocatedVariants will be null
   */
  @Operation(summary = "- other variants co-located at the same amino acid position")
  @GetMapping(value = "/population/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PopulationObservation> getPopulationObservation(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position) {
    PopulationObservation variations = service.getPopulationObservation(accession, position);
    return new ResponseEntity<>(variations, HttpStatus.OK);
  }

  /**
   * Returns the position in PDB structures for the input variant. Other structures may exist for the protein
   * but only those containing the input residue position are listed. This is a wrapper API based upon
   * PDBe API (best structures) which allows us to retrieve information from a single residue. If annotations for
   * the entire protein are required or if further annotations from structure are needed then please use
   * the <a href="https://www.ebi.ac.uk/pdbe/graph-api/pdbe_doc/" target="_new">PDBe API</a> directly.
   *
   * @param accession Uniprot accession
   * @param position  Amino acid position
   * @return List of <code>PDBeStructure</code> Mappings from UniProt position to position in all relevant PDB structures
   */
  @Operation(summary = "- retrieve mappings of protein position to structures")
  @GetMapping(value = "/structure/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<PDBeStructure>> getStructure(
    @Parameter(example = "Q9NUW8") @PathVariable("accession") String accession,
    @Parameter(example = "493") @PathVariable("position") int position) {
    List<PDBeStructure> object = service.getStructure(accession, position);
    return new ResponseEntity<>(object, HttpStatus.OK);
  }
}
