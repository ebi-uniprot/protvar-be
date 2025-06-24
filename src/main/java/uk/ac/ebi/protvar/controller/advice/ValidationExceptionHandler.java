package uk.ac.ebi.protvar.controller.advice;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class ValidationExceptionHandler {

    /**
     * Handles JSON deserialization errors, specifically for invalid enum values.
     *
     * When a client sends a value for an enum field that does not match any enum constant,
     * Jackson throws an InvalidFormatException wrapped in a HttpMessageNotReadableException.
     *
     * This handler extracts the field name, invalid value, and target enum type,
     * then returns a clear, custom error message indicating the invalid enum value.
     *
     * If the error is not an enum deserialization issue, it returns a generic malformed JSON message.
     *
     * IMPORTANT:
     * This exception occurs during the JSON parsing/deserialization phase,
     * before Spring MVC invokes the controller method or validation.
     *
     * Therefore, if this exception occurs, the MethodArgumentNotValidException handler
     * will NOT be triggered for that request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFormat(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormatException) {
            String fieldName = invalidFormatException.getPath().get(0).getFieldName();
            String targetType = invalidFormatException.getTargetType().getSimpleName();
            String invalidValue = String.valueOf(invalidFormatException.getValue());

            Map<String, String> error = new HashMap<>();
            error.put(fieldName, "Invalid value '" + invalidValue + "' for " + targetType);
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
        // fallback generic error message for malformed JSON or other issues
        return new ResponseEntity<>(Map.of("error", "Malformed JSON request"), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles exceptions thrown when @Valid validation on method arguments fails,
     * typically for @RequestBody or @ModelAttribute objects that violate constraints
     * like @NotBlank, @Size, @Email, etc.
     *
     * This catches MethodArgumentNotValidException, which contains detailed info about
     * which fields failed validation and why.
     *
     * The method builds a map of field names to their respective validation messages,
     * e.g. { "username": "Username is required", "email": "Email should be valid" },
     * and returns this map with HTTP 400 Bad Request status.
     *
     * This provides the client a simple, consistent JSON error response describing all
     * validation errors in the request.
     *
     * IMPORTANT:
     * This exception only occurs if JSON deserialization succeeds.
     * Validation runs after deserialization and before the controller method execution.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}