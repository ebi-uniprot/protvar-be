package uk.ac.ebi.protvar.controller.example;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Hidden // Example base controller, excluded from Swagger UI
@RestController
@RequestMapping("/api/users")
public class UserController {


    // curl -X POST "http://localhost:8080/ProtVar/api/users" \
    //      -H "Content-Type: application/json" \
    //      -d '{"username": "johndoe", "email": "john@example.com", "password": "password123"}'
    @PostMapping(/*path = "/json"*/consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createUserJson(@Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok("User created (JSON): " + userDTO.getUsername());
    }

    // curl -X POST "http://localhost:8080/ProtVar/api/users" \
    //      -H "Content-Type: application/x-www-form-urlencoded" \
    //      -d "username=johndoe&email=john@example.com&password=password123"
    @PostMapping(/*path = "/form"*/consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> createUserForm(@Valid
            @RequestBody @ModelAttribute UserDTO userDTO) {
        return ResponseEntity.ok("User created from Form: " + userDTO.getUsername());
    }

    // Using UserDTO2 with manual conversion for role
    @PostMapping(path = "/json2", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createUserJson2(@Valid @RequestBody UserDTO2 userDTO) {
        UserRole roleEnum;
        UserRole otherRoleEnum = null;

        try {
            roleEnum = UserRole.valueOf(userDTO.getRole());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("role", "Invalid value '" + userDTO.getRole() + "' for role"));
        }

        if (userDTO.getOtherRole() != null && !userDTO.getOtherRole().isBlank()) {
            try {
                otherRoleEnum = UserRole.valueOf(userDTO.getOtherRole());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("otherRole", "Invalid value '" + userDTO.getOtherRole() + "' for otherRole"));
            }
        }

        // Proceed with roleEnum and otherRoleEnum validated and converted

        return ResponseEntity.ok("User created (JSON): " + userDTO.getUsername());
    }
    /*
    Alternatively, define a StringToUserRoleConverter
    @Component
    public class StringToUserRoleConverter implements Converter<String, UserRole> {

        @Override
        public UserRole convert(String source) {
            if (source == null || source.isBlank()) {
                return null;  // or throw if you want to force non-null inputs
            }
            try {
                return UserRole.valueOf(source.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid UserRole: " + source);
            }
        }
    }

    Then, controller can be simplified:
    @PostMapping(path = "/json2", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createUserJson(@Valid @RequestBody UserDTO2 userDTO) {
        // no manual conversion needed here
        return ResponseEntity.ok("User created (JSON): " + userDTO.getUsername());
    }

    Add an additional handler method in @ControllerAdvice:
    @ExceptionHandler({ConversionFailedException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleConversionErrors(Exception ex) {
        Map<String, String> errors = new HashMap<>();
        // Extract meaningful message from cause or exception
        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        errors.put("error", message);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

     */
}