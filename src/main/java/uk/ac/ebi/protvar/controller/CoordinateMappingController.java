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
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.processor.InputProcessor;
import uk.ac.ebi.protvar.model.response.MappingResponse;
import uk.ac.ebi.protvar.model.response.Message;

import java.util.List;

@Tag(name = "Coordinate Mapping")
@RestController
@CrossOrigin
@AllArgsConstructor
public class CoordinateMappingController {
  public final static int MAX_INPUT = 1000;
  public final static String ASSEMBLY_DESC = "Specify the human genome assembly version. Accepted values are: GRCh38/h38/38, GRCh37/h37/37 or AUTO (default, auto-detects the version).";
  private MappingFetcher mappingFetcher;

  @Operation(
          summary = "Genomic-Protein Mappings for Various Input Formats",
          description = "Retrieve genomic-protein mappings for a list of inputs in any supported format. These formats include " +
                  "genomic (VCF, gnomAD, HGVSg, and various custom formats), protein (HGVSp and various custom formats), " +
                  "cDNA (HGVSc), and variant ID (dbSNP, ClinVar, COSMIC). UPDATE: This endpoint processes up to 1000 inputs per request now. " +
                  "If the input size exceeds this limit, only the first 1000 inputs will be processed and returned. For larger inputs, " +
                  "please use the new mapping paginated endpoint, which returns a paginated response."
  )
  @PostMapping(value = "/mappings", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MappingResponse> mappings(
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
                  content = @Content(
                          examples = @ExampleObject(value = "[\"19 1010539 G C\",\"P80404 Gln56Arg\", \"rs1042779\"]")
                  )
          )
        @RequestBody List<String> inputs,
        @Parameter(description = "Include functional annotations in results")
        @RequestParam(required = false, defaultValue = "false") boolean function,
        @Parameter(description = "Include population annotations (residue co-located variants and disease associations) in results")
        @RequestParam(required = false, defaultValue = "false") boolean population,
        @Parameter(description = "Include structural annotations in results")
        @RequestParam(required = false, defaultValue = "false") boolean structure,
        @Parameter(description = ASSEMBLY_DESC)
        @RequestParam(required = false) String assembly
  ) {
    List<String> processList = inputs;
    if (inputs.size() > MAX_INPUT) {
      processList = inputs.subList(0, MAX_INPUT);
    }

    InputParams params = InputParams.builder()
            .inputs(InputProcessor.parse(processList))
            .fun(function)
            .pop(population)
            .str(structure)
            .assembly(assembly)
            .summarise(true)
            .build();

    MappingResponse mappingResponse = mappingFetcher.getMapping(params);
    if (inputs.size() > MAX_INPUT && mappingResponse != null && mappingResponse.getMessages() != null) {
      mappingResponse.getMessages().add(new Message(Message.MessageType.WARN, String.format("Processed first %d inputs only.", MAX_INPUT)));
    }
    return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
  }

  @Operation(
          summary = "Retrieve Mapping for a Single Input Query",
          description = "Fetch the genomic-protein mapping for a single input query provided as search parameter in the URL. " +
                  "The input should be in a supported format, such as genomic coordinates, variant identifiers, or protein change notations. " +
                  "Default input params: annotations false, assembly null, and summarise false."
  )
  @GetMapping(value = "/mapping", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MappingResponse> mapping(
          @Parameter(description = "The input query in a supported format.", example = "19-1010539-G-C")
          @RequestParam String input) {
      InputParams params = InputParams.builder()
              .inputs(InputProcessor.parse(List.of(input)))
              .build();
    MappingResponse mappingResponse = mappingFetcher.getMapping(params);
    return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
  }

}
