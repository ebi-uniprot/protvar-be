package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.fetcher.AssemblyMappingFetcher;
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.AssemblyMappingResponse;
import java.util.List;

@Tag(name = "Assembly Mapping")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class AssemblyMappingController {

    private final AssemblyMappingFetcher assemblyMappingFetcher;

    /**
     * @param inputs List of genomic coordinates
     * @param from   From assembly e.g. `37`, `h37`, or `GRCh37`
     * @param to     To assembly e.g. `38`, `h38`, or `GRCh38`
     * @return <code>AssemblyMappingResponse</code> see below schema for more details
     */
    @Operation(summary = "Convert genomic coordinates between GRCh37 and GRCh38")
    @PostMapping(value = "/assembly/mappings/{from}/{to}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssemblyMappingResponse> convert(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
            @ExampleObject(value = "[\"1 12065827\",\"1 12476789\", \"1 15772212\"]"))})
            @RequestBody List<String> inputs,
            @Parameter(example = "37") @PathVariable("from") String from,
            @Parameter(example = "38") @PathVariable("to") String to
    ) {
        Assembly fromAssembly = Assembly.of(from);
        Assembly toAssembly = Assembly.of(to);

        if (fromAssembly != null && toAssembly != null
                && fromAssembly != toAssembly) {
            AssemblyMappingResponse result = assemblyMappingFetcher.getMappings(inputs, fromAssembly, toAssembly);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}