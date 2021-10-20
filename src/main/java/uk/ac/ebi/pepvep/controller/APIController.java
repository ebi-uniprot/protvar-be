package uk.ac.ebi.pepvep.controller;

import java.util.List;

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

import io.swagger.annotations.Api;
import uk.ac.ebi.pepvep.model.response.GenomeProteinMapping;
import uk.ac.ebi.pepvep.model.response.MappingResponse;
import uk.ac.ebi.pepvep.model.response.PopulationObservation;
import uk.ac.ebi.pepvep.model.response.Protein;
import uk.ac.ebi.pepvep.service.APIService;

@Api(tags = "PepVEP")
@RestController
@CrossOrigin
@AllArgsConstructor
public class APIController {
	private APIService service;

	@GetMapping(value = "/function/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Protein> getFunction(@PathVariable("accession") String accession,
                                             @PathVariable("position") int position) {
		Protein protein = service.getProtein(accession, position);
		return new ResponseEntity<>(protein, HttpStatus.OK);
	}

	@GetMapping(value = "/population/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PopulationObservation> getPopulationObservation(@PathVariable("accession") String accession,
                                                            @PathVariable("position") int position) {
		PopulationObservation variations = service.getPopulationObservation(accession, position);
		return new ResponseEntity<>(variations, HttpStatus.OK);
	}

	@GetMapping(value = "/structure/{accession}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getStructure(@PathVariable("accession") String accession,
			@PathVariable("position") int position) {
		Object object = service.getStructure(accession, position);
		return new ResponseEntity<>(object, HttpStatus.OK);
	}

	@GetMapping(value = "/mapping/{chromosome}/{position}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<GenomeProteinMapping> getGenomeProteinMapping(@PathVariable("chromosome") String chromosome,
                                                                      @PathVariable("position") Long position,
                                                                      @RequestParam(name = "refAllele") String refAllele,
                                                                      @RequestParam(name = "altAllele") String altAllele,
                                                                      @RequestParam(name = "function", required = false, defaultValue = "false") boolean function,
                                                                      @RequestParam(name = "variation", required = false, defaultValue = "false") boolean variation,
                                                                      @RequestParam(name = "structure", required = false, defaultValue = "false") boolean structure) {
		GenomeProteinMapping mappings = service.getMapping(chromosome, position, "", refAllele, altAllele, function,
				variation, structure);
		return new ResponseEntity<>(mappings, HttpStatus.OK);
	}

	@PostMapping(value = "/mapping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MappingResponse> getGenomeProteinMappings(@RequestBody List<String> inputs,
                                                                  @RequestParam(name = "function", required = false, defaultValue = "false") boolean function,
                                                                  @RequestParam(name = "variation", required = false, defaultValue = "false") boolean variation,
                                                                  @RequestParam(name = "structure", required = false, defaultValue = "false") boolean structure) {
		MappingResponse mappingResponse = service.getMappings(inputs, function, variation, structure);
		return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
	}
}
