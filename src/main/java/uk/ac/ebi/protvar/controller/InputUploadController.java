package uk.ac.ebi.protvar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ebi.protvar.model.UserInputRequest;
import uk.ac.ebi.protvar.service.UserInputService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Tag(name = "Input Upload")
@RestController
@CrossOrigin
@RequiredArgsConstructor
public class InputUploadController {
    private final UserInputService userInputService;

    @Operation(summary = "Upload raw input file and get an input ID")
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam MultipartFile file,
                                        @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        try {
            String rawInput = new String(file.getBytes(), StandardCharsets.UTF_8);
            String id = userInputService.processInput(UserInputRequest.builder()
                    .rawInput(rawInput)
                    .assembly(assembly)
                    .build());
            return ResponseEntity.ok(Map.of("inputId", id));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("File upload error: " + e.getMessage());
        }
    }

    @Operation(summary = "Upload raw text input and get an input ID")
    @PostMapping(value = "/upload/text", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadText(@RequestBody String rawInput,
                                        @RequestParam(required = false, defaultValue = "AUTO") String assembly) {
        String id = userInputService.processInput(UserInputRequest.builder()
                .rawInput(rawInput)
                .assembly(assembly)
                .build());
        return ResponseEntity.ok(Map.of("inputId", id));
    }

}
