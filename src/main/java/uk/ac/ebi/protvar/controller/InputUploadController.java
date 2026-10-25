package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.InputRequest;
import uk.ac.ebi.protvar.model.response.InputUploadResponse;
import uk.ac.ebi.protvar.service.InputService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Tag(name = "Input")
@RestController
@RequestMapping("/input")
@CrossOrigin
@RequiredArgsConstructor
public class InputUploadController {
    private final InputService inputService;

    @Operation(
        summary = "Upload a variant file",
        description = "Plain-text file, one variant per line, any supported format. Returns an inputId for /mapping and /download."
    )
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "Text file, one variant per line.")
            @RequestParam MultipartFile file,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        try {
            String rawInput = new String(file.getBytes(), StandardCharsets.UTF_8);
            String inputId = inputService.processInput(InputRequest.builder()
                    .rawInput(rawInput)
                    .assembly(assembly)
                    .build());
            //return ResponseEntity.ok(Map.of("inputId", inputId));
            return ResponseEntity.ok(new InputUploadResponse(inputId));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("File upload error: " + e.getMessage());
        }
    }

    @Operation(
        summary = "Submit variants as text",
        description = "One variant per line, any supported format. Returns an inputId for /mapping and /download."
    )
    @PostMapping(value = "/text", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadText(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "One variant per line.",
                content = @Content(
                    examples = @ExampleObject(value = "19 1010539 G C\nP80404 Gln56Arg\nrs1042779")
                )
            )
            @RequestBody String text,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        String inputId = inputService.processInput(InputRequest.builder()
                .rawInput(text)
                .assembly(assembly)
                .build());
        return ResponseEntity.ok(new InputUploadResponse(inputId));
    }

}
