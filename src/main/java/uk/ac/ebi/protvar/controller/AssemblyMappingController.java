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
import uk.ac.ebi.protvar.model.grc.Assembly;
import uk.ac.ebi.protvar.model.response.AssemblyMappingResponse;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.service.APIService;

import java.util.List;

@Tag(name = "Assembly Mapping", description = "Human genome assembly mapping service.")
@RestController
@CrossOrigin
@AllArgsConstructor
public class AssemblyMappingController {
    private APIService service;

    /**
     * Requires a list of genomic coordinates (chromosome and genomic position pairs) and input/output assembly versions.
     * Returns successfully mapped equivalent coordinates in the output version. In addition to the equivalent chromosome
     * and position, it also returns the reference allele for both versions. This should normally match but in some cases
     * can differ.
     *
     * @param inputs list of genomic coordinates
     * @param from   input assembly version e.g. 37
     * @param to     output assembly version e.g. 38
     * @return <code>AssemblyMappingResponse</code> see below schema for more details
     */
    @Operation(summary = "- convert genomic coordinates")
    @PostMapping(value = "/assembly/mappings/{from}/{to}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssemblyMappingResponse> convert(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {@Content(examples =
            @ExampleObject(value = "[\"1 12065827\",\"1 12476789\", \"1 15772212\"]"))})
            @RequestBody List<String> inputs,
            @Parameter(example = "37") @PathVariable("from") String from,
            @Parameter(example = "38") @PathVariable("to") String to
    ) {
        if (Assembly.VALID_ASSEMBLY_VERSIONS.contains(from)
                && Assembly.VALID_ASSEMBLY_VERSIONS.contains(to)
                && !from.equals(to)) {
            AssemblyMappingResponse result = service.getAssemblyMapping(inputs, from, to);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}