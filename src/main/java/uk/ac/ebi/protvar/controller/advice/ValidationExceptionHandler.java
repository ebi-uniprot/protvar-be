package uk.ac.ebi.protvar.controller.advice;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class ValidationExceptionHandler {

    /**
     * Handles JSON deserialization errors, specifically for invalid enum values.
     *
     * When a client sends a value for an enum field that does not match any enum constant,
     * Jackson throws an InvalidFormatException wrapped in a HttpMessageNotReadableException.
     *
     * This handler walks the exception cause chain to find the InvalidFormatException,
     * extracts the field name, invalid value, and target enum type, then returns a clear,
     * custom error message with the list of valid values.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFormat(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();

        // Walk the cause chain to find specific exception types
        while (cause != null) {
            if (cause instanceof InvalidFormatException invalidFormatException) {
                return handleInvalidFormatException(invalidFormatException);
            }
            if (cause instanceof ValueInstantiationException valueInstantiationException) {
                return handleValueInstantiationException(valueInstantiationException);
            }
            if (cause instanceof JsonMappingException jsonMappingException) {
                return handleJsonMappingException(jsonMappingException);
            }
            cause = cause.getCause();
        }

        // Generic fallback for other JSON parsing errors
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Malformed JSON request");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles InvalidFormatException (when a value cannot be converted to the target type)
     */
    private ResponseEntity<Map<String, Object>> handleInvalidFormatException(
            InvalidFormatException invalidFormatException) {

        Map<String, Object> error = new HashMap<>();

        String fieldPath = extractFullFieldPath(invalidFormatException);
        String message = extractRootCauseMessage(invalidFormatException);

        if (message == null) {
            String simpleTypeName = invalidFormatException.getTargetType().getSimpleName();
            String invalidValue = String.valueOf(invalidFormatException.getValue());
            message = String.format("Invalid %s: '%s'", simpleTypeName, invalidValue);
        }

        error.put(fieldPath, message);

        // If it's an enum, include valid values
        if (invalidFormatException.getTargetType().isEnum()) {
            addValidEnumValues(error, invalidFormatException.getTargetType());
        }

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ValueInstantiationException (when object construction fails, e.g., in @JsonCreator)
     */
    private ResponseEntity<Map<String, Object>> handleValueInstantiationException(
            ValueInstantiationException valueInstantiationException) {

        Map<String, Object> error = new HashMap<>();

        String fieldPath = extractFullFieldPath(valueInstantiationException);
        String message = extractRootCauseMessage(valueInstantiationException);

        if (message == null) {
            String simpleTypeName = valueInstantiationException.getType().getRawClass().getSimpleName();
            message = String.format("Invalid %s", simpleTypeName);
        }

        error.put(fieldPath, message);

        // If it's an enum type, include valid values
        Class<?> targetType = valueInstantiationException.getType().getRawClass();
        if (targetType.isEnum()) {
            addValidEnumValues(error, targetType);
        }

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles general JSON mapping exceptions
     */
    private ResponseEntity<Map<String, Object>> handleJsonMappingException(
            JsonMappingException jsonMappingException) {

        Map<String, Object> error = new HashMap<>();
        String fieldPath = extractFullFieldPath(jsonMappingException);
        String message = extractRootCauseMessage(jsonMappingException);

        if (message == null) {
            message = "Invalid value or format";
        }

        error.put(fieldPath, message);

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Extracts the full field path from the JSON path in the exception.
     */
    private String extractFullFieldPath(JsonMappingException exception) {
        if (exception.getPath() == null || exception.getPath().isEmpty()) {
            return "unknown";
        }

        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : exception.getPath()) {
            if (ref.getFieldName() != null) {
                if (path.length() > 0) {
                    path.append(".");
                }
                path.append(ref.getFieldName());
            }
            if (ref.getIndex() >= 0) {
                path.append("[").append(ref.getIndex()).append("]");
            }
        }

        return path.length() > 0 ? path.toString() : "unknown";
    }

    /**
     * Extracts the root cause message from IllegalArgumentException thrown by enum parse() methods.
     */
    private String extractRootCauseMessage(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }

        return null;
    }

    /**
     * Adds valid enum values to the error response
     */
    private void addValidEnumValues(Map<String, Object> error, Class<?> enumClass) {
        if (enumClass.isEnum()) {
            String validValues = Arrays.stream(enumClass.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            error.put("validValues", validValues);
        }
    }

    /**
     * Handles validation errors from @Valid annotation.
     *
     * This catches MethodArgumentNotValidException, which contains detailed info about
     * which fields failed validation and why (e.g., @NotBlank, @Min, @Max violations).
     *
     * IMPORTANT:
     * This exception only occurs if JSON deserialization succeeds.
     * Validation runs after deserialization and before the controller method execution.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}