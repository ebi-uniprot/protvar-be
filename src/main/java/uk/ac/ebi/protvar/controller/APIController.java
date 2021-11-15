package uk.ac.ebi.protvar.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.ac.ebi.protvar.model.response.*;
import uk.ac.ebi.protvar.service.APIService;

@Tag(name = "Search", description = "Variation mapping and detail annotations")
@RestController
@CrossOrigin
@AllArgsConstructor
public class APIController {
	private APIService service;

	/**
	 * Return protein information related to specific position and dropping everything else which is ot relevant to position.
	 * This is a wrapper api upon https://www.ebi.ac.uk/proteins/api/proteins/
	 * <a href="https://www.ebi.ac.uk/proteins/api/proteins/" target="_new">protein api</a>
	 * to help us generate data for our web application and download. Please consider calling protein api directly if you
	 * need data related to uniprot accession. That would be lot faster for you
	 *
   * @param accession Uniprot accession e-g Q9NUW8
   * @param position  Amino Acid position e-g 493
	 * @return <code>Protein</code> information on accession
	 */
	@GetMapping(value = "/function/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Protein> getFunction(@PathVariable("accession") String accession,
                                             @PathVariable("position") int position) {
		Protein protein = service.getProtein(accession, position);
		return new ResponseEntity<>(protein, HttpStatus.OK);
	}

	/**
	 * Get population observation information for given position and dropping everything else which is ot relevant using
	 * <a href="https://www.ebi.ac.uk/proteins/api/doc/#/variation" target="_new">variation api</a>
	 * with endpoint https://www.ebi.ac.uk/proteins/api/variation/ Please consider calling variation api directly if you
	 * need related data. That would be lot faster for you
	 *
   * @param accession Uniprot accession e-g Q9NUW8
   * @param position  Amino Acid position e-g 493
	 * @return <code>PopulationObservation</code> List of proteinColocatedVariants and genomicColocatedVariants.
	 * For now genomicColocatedVariants will be null
	 */
	@GetMapping(value = "/population/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PopulationObservation> getPopulationObservation(@PathVariable("accession") String accession,
                                                            @PathVariable("position") int position) {
		PopulationObservation variations = service.getPopulationObservation(accession, position);
		return new ResponseEntity<>(variations, HttpStatus.OK);
	}

  /**
   * Retrieve structural information of protein from PDB. This is a wrapper on
   * <a href="https://www.ebi.ac.uk/pdbe/pdbe-rest-api" target="_new">PDB api</a> and using
   * https://www.ebi.ac.uk/pdbe/graph-api/mappings/best_structures/ Please consider calling pdb api directly if you
   * need related data. That would be lot faster for you
   *
   * @param accession Uniprot accession e-g Q9NUW8
   * @param position  Amino Acid position e-g 493
   * @return List of <code>PDBeStructure</code> containing information about accession
   */
	@GetMapping(value = "/structure/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<PDBeStructure>> getStructure(@PathVariable("accession") String accession,
			@PathVariable("position") int position) {
		List<PDBeStructure> object = service.getStructure(accession, position);
		return new ResponseEntity<>(object, HttpStatus.OK);
	}

  @Hidden
	@GetMapping(value = "/mapping/{chromosome}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<GenomeProteinMapping> getGenomeProteinMapping(@PathVariable String chromosome,
    @PathVariable Long position,
    @RequestParam String refAllele,
    @RequestParam String altAllele,
    @RequestParam(required = false, defaultValue = "false") boolean function,
    @RequestParam(required = false, defaultValue = "false") boolean population,
    @RequestParam(required = false, defaultValue = "false") boolean structure) {
		GenomeProteinMapping mappings = service.getMapping(chromosome, position, "", refAllele, altAllele, function,
      population, structure);
		return new ResponseEntity<>(mappings, HttpStatus.OK);
	}

  /**
   * Maps user provided mapping to check variations against database
   *
   * @param inputs Data which you want to analysis. List of String
   *               ["19 1010539 G C","14 89993420 A/G", "10 87933147 rs7565837 C/T"]
   * @return <code>MappingResponse</code> see in below schema for more details
   */
	@PostMapping(value = "/mapping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MappingResponse> getGenomeProteinMappings(@RequestBody List<String> inputs,
    @Parameter(hidden = true) @RequestParam(required = false, defaultValue = "false") boolean function,
    @Parameter(hidden = true) @RequestParam(required = false, defaultValue = "false") boolean population,
    @Parameter(hidden = true) @RequestParam(required = false, defaultValue = "false") boolean structure) {
		MappingResponse mappingResponse = service.getMappings(inputs, function, population, structure);
		return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
	}
}
