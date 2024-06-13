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
import uk.ac.ebi.protvar.model.response.Message;
import uk.ac.ebi.protvar.service.MappingService;

import java.util.List;

@Tag(name = "Coordinate Mapping")
@RestController
@CrossOrigin
@AllArgsConstructor
public class CoordinateMappingController {
  private final static int MAX_INPUT = 1000;
  private MappingService mappingService;

  /**
   * Genomic-protein coordinate mapping.
   *
   * @param inputs List of inputs in any supported format. If input list size is greater than
   *               `${MAX_INPUT}`, only the first `${MAX_INPUT}` input is processed and returned.
   *               For larger input, use the new mappings endpoint that returns paginated response.
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
    List<String> processList = inputs;
    if (inputs.size() > MAX_INPUT) {
      processList = inputs.subList(0, MAX_INPUT);
    }
    MappingResponse mappingResponse = mappingService.getMapping(processList, function, population, structure, assembly);
    if (inputs.size() > MAX_INPUT && mappingResponse != null && mappingResponse.getMessages() != null) {
      mappingResponse.getMessages().add(new Message(Message.MessageType.WARN, String.format("Processed first %d inputs only.", MAX_INPUT)));
    }
    return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
  }

  @Operation(summary = "Retrieve mapping for single input query specified as path parameter in the URL.")
  @GetMapping(value = "/mapping/query/{input}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MappingResponse> queryInput(
          @Parameter(example = "19 1010539 G C") @PathVariable("input") String input) {
    MappingResponse mappingResponse = mappingService.getMapping(List.of(input), false, false, false, null);
    return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
  }

}
