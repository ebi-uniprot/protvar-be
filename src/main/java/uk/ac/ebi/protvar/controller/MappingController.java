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
import uk.ac.ebi.protvar.service.APIService;

import java.util.List;

@Tag(name = "Genomic Coordinate – Amino Acid Position Mappings", description = "Retrieve positions of genomic variants in all protein isoforms")
@RestController
@CrossOrigin
@AllArgsConstructor
public class
MappingController {
  private APIService apiService;

  /**
   * Requires a list of genomic coordinate variant inputs in VCF format and returns mappings to all known isoforms
   * as a json object. Information including names, IDs and positions is included for transcripts and isoforms and
   * the UniProt canonical isoform is identified.
   *
   * @param inputs Variants which you wish to retrieve annotations for in json string array format (example shown below):
   * @return <code>MappingResponse</code> see below schema for more details
   */
  @Operation(summary = "- retrieve amino acid positions in proteins from a list of genomic coordinates (specified" +
          " in VCF or HGVS format), protein positions, or variant IDs")
  @PostMapping(value = "/mappings", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MappingResponse> getGenomeProteinMappings(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
    @ExampleObject(value = "[\"19 1010539 G C\",\"P80404 Gln56Arg\", \"rs1042779\"]"))})
    @RequestBody List<String> inputs,
    @Parameter(description = "Include functional annotations in results")
    @RequestParam(required = false, defaultValue = "false") boolean function,
    @Parameter(description = "Include population annotations (residue co-located variants and disease associations) in results")
    @RequestParam(required = false, defaultValue = "false") boolean population,
    @Parameter(description = "Include structural annotations in results")
    @RequestParam(required = false, defaultValue = "false") boolean structure,
    @Parameter(description = "Human genome assembly version. For e.g. GRCh38/h38/38, GRCh37/h37/37 or AUTO. If unspecified or cannot determine version, defaults to GRCh38")
    @RequestParam(required = false) String assembly
  ) {
    MappingResponse mappingResponse = apiService.getMappings(inputs, function, population, structure, assembly);
    return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
  }

}
