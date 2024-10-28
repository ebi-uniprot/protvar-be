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
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.fetcher.MappingFetcher;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.params.InputParams;
import uk.ac.ebi.protvar.input.processor.BuildProcessor;
import uk.ac.ebi.protvar.input.processor.InputProcessor;
import uk.ac.ebi.protvar.model.grc.Assembly;
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
    private BuildProcessor buildProcessor;

    @Operation(
            summary = "Legacy genomic-to-protein mapping endpoint.",
            description = "Retrieve genomic-protein mappings for the given inputs list. Refer to the [ProtVar Help](https://www.ebi.ac.uk/ProtVar/help#supported-format) for supported input formats. " +
                    "<br/>**Note: This endpoint is deprecated and will be removed in a future version.**" +
                    "<br/>**Note: This endpoint is limited to up to 1000 inputs per request.**" +
                    "<br/>Please use the paginated mapping endpoint instead."
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
        List<UserInput> userInputs = InputProcessor.parse(processList);
        InputBuild inputBuild = null;
        if (Assembly.autodetect(assembly)) {
            List<UserInput> genomicInputs = buildProcessor.filterGenomicInputs(processList);
            if (!genomicInputs.isEmpty()) {
                inputBuild = buildProcessor.detect(genomicInputs);
            }
        }

        InputParams params = InputParams.builder()
                .inputs(userInputs)
                .fun(function)
                .pop(population)
                .str(structure)
                .assembly(assembly)
                .inputBuild(inputBuild)
                .summarise(true) // not needed anymore
                .build();

        MappingResponse mappingResponse = mappingFetcher.getMapping(params);
        if (inputs.size() > MAX_INPUT && mappingResponse != null && mappingResponse.getMessages() != null) {
            mappingResponse.getMessages().add(new Message(Message.MessageType.WARN, String.format("Processed first %d inputs only.", MAX_INPUT)));
        }

        // Post-fetch: add input summary to response
        if (mappingResponse != null) {
            String inputSummary = InputProcessor.summary(userInputs).toString();
            mappingResponse.getMessages().add(new Message(Message.MessageType.INFO, inputSummary));
            if (inputBuild != null && inputBuild.getMessage() != null) {
                mappingResponse.getMessages().add(inputBuild.getMessage());
            }
        }


        return new ResponseEntity<>(mappingResponse, HttpStatus.OK);
    }

    // TODO
    // perhaps a single GET /mapping endpoint that takes an InputType param
    // inputType=ID|PROTEIN|SINGLE_VARIANT
    // input=id or accession or single (or could be list of) input/variants
    // note: current single_query and /mapping/query endpoint seems to be for the same purpose (or direct query, or non-cached input)
    // move below to PagedMappingResponse/isolate this endpoint

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
