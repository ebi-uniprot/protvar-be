package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.PagedMappingResponse;
import uk.ac.ebi.protvar.service.MappingService;

import java.util.List;

@Tag(name = "Coordinate Mapping")
@RestController
@CrossOrigin
@AllArgsConstructor
public class CoordinateMappingController {
  private MappingService mappingService;

  /**
   * Retrieves genomic-to-protein mappings
   *
   * @param inputs List of inputs in genomic, cDNA, protein or ID format
   * @return <code>MappingResponse</code>
   */
  @Operation(summary = "Retrieve mappings for the provided genomic, cDNA, protein or ID inputs")
  @PostMapping(value = "/mappings", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MappingResponse> mappings(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
    @ExampleObject(value = "[\"19 1010539 G C\",\"P80404 Gln56Arg\", \"rs1042779\"]"))})
    @RequestBody List<String> inputs,
    @Parameter(description = "Include functional annotations in results")
    @RequestParam(required = false, defaultValue = "false") boolean function,
    @Parameter(description = "Include population annotations (residue co-located variants and disease associations) in results")
    @RequestParam(required = false, defaultValue = "false") boolean population,
    @Parameter(description = "Include structural annotations in results")
    @RequestParam(required = false, defaultValue = "false") boolean structure,
    @Parameter(description = "Human genome assembly version. Accepted values: GRCh38/h38/38, GRCh37/h37/37 or AUTO. Defaults to auto-detect")
    @RequestParam(required = false) String assembly
  ) {
    MappingResponse mappingResponse = mappingService.getMapping(inputs, function, population, structure, assembly);
    return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
  }


  @Operation(summary = "Retrieve all mappings for the provided UniProt accession (WORK IN PROGRESS)")
  @GetMapping(value = "/mappings/{accession}")
  public ResponseEntity<PagedMappingResponse> mappingsAccession(
          @Parameter(example = "Q9UHP9") @PathVariable("accession") String accession,
          @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
          @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize) {
    PagedMappingResponse response = mappingService.getMappingByAccession(accession, pageNo, pageSize);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
