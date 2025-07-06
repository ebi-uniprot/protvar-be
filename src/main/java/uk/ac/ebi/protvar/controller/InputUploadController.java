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
import uk.ac.ebi.protvar.model.UserInputRequest;
import uk.ac.ebi.protvar.model.response.InputUploadResponse;
import uk.ac.ebi.protvar.service.UserInputService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Tag(
    name = "Input Upload",
    description = "Upload variant data via text or file and receive an input ID for downstream queries."
)
@RestController
@RequestMapping("/input")
@CrossOrigin
@RequiredArgsConstructor
public class InputUploadController {
    private final UserInputService userInputService;

    @Operation(
        summary = "Upload variant input file and receive a corresponding input ID",
        description = "Accepts a plain text file containing variants (one per line) in supported formats. Returns a unique input ID to be used in downstream variant queries."
    )
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "A text file where each line contains a variant in a supported format.")
            @RequestParam MultipartFile file,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        try {
            String rawInput = new String(file.getBytes(), StandardCharsets.UTF_8);
            String inputId = userInputService.processInput(UserInputRequest.builder()
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
        summary = "Upload variant text and receive a corresponding input ID",
        description = "Accepts raw text with one variant per line in a supported format. Returns a unique input ID for later use."
    )
    @PostMapping(value = "/text", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadText(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Raw text input where each line contains a variant.",
                content = @Content(
                    examples = @ExampleObject(value = "19 1010539 G C\nP80404 Gln56Arg\nrs1042779")
                )
            )
            @RequestBody String text,
            @Parameter(description = MappingRequest.ASSEMBLY_DESC)
            @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        String inputId = userInputService.processInput(UserInputRequest.builder()
                .rawInput(text)
                .assembly(assembly)
                .build());
        return ResponseEntity.ok(new InputUploadResponse(inputId));
    }

}
