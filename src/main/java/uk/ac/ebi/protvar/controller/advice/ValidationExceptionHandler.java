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
     * Handles JSON deserialization errors. Walks the cause chain to find a
     * specific Jackson exception (InvalidFormatException, ValueInstantiationException,
     * or JsonMappingException) and returns a clear error response. Includes the
     * list of valid values when the target is an enum.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFormat(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();

        while (cause != null) {
            if (cause instanceof InvalidFormatException ife) {
                return handleInvalidFormatException(ife);
            }
            if (cause instanceof ValueInstantiationException vie) {
                return handleValueInstantiationException(vie);
            }
            if (cause instanceof JsonMappingException jme) {
                return handleJsonMappingException(jme);
            }
            cause = cause.getCause();
        }

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Malformed JSON request");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * InvalidFormatException — value cannot be converted to the target type
     * (e.g. an unknown enum constant).
     */
    private ResponseEntity<Map<String, Object>> handleInvalidFormatException(InvalidFormatException ife) {
        Map<String, Object> error = new HashMap<>();
        String fieldPath = extractFullFieldPath(ife);
        String message = extractRootCauseMessage(ife);

        if (message == null) {
            String simpleTypeName = ife.getTargetType().getSimpleName();
            String invalidValue = String.valueOf(ife.getValue());
            message = String.format("Invalid %s: '%s'", simpleTypeName, invalidValue);
        }
        error.put(fieldPath, message);

        if (ife.getTargetType().isEnum()) {
            addValidEnumValues(error, ife.getTargetType());
        }
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * ValueInstantiationException — object construction failed, typically when an
     * enum's @JsonCreator factory throws IllegalArgumentException.
     */
    private ResponseEntity<Map<String, Object>> handleValueInstantiationException(ValueInstantiationException vie) {
        Map<String, Object> error = new HashMap<>();
        String fieldPath = extractFullFieldPath(vie);
        String message = extractRootCauseMessage(vie);

        if (message == null) {
            String simpleTypeName = vie.getType().getRawClass().getSimpleName();
            message = String.format("Invalid %s", simpleTypeName);
        }
        error.put(fieldPath, message);

        Class<?> targetType = vie.getType().getRawClass();
        if (targetType.isEnum()) {
            addValidEnumValues(error, targetType);
        }
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Generic JsonMappingException fallback.
     */
    private ResponseEntity<Map<String, Object>> handleJsonMappingException(JsonMappingException jme) {
        Map<String, Object> error = new HashMap<>();
        String fieldPath = extractFullFieldPath(jme);
        String message = extractRootCauseMessage(jme);
        if (message == null) {
            message = "Invalid value or format";
        }
        error.put(fieldPath, message);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Builds a dotted field path from the Jackson reference chain
     * (e.g. {@code "cadd[0]"} or {@code "ids[0].type"}).
     */
    private String extractFullFieldPath(JsonMappingException exception) {
        if (exception.getPath() == null || exception.getPath().isEmpty()) {
            return "unknown";
        }
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : exception.getPath()) {
            if (ref.getFieldName() != null) {
                if (path.length() > 0) path.append(".");
                path.append(ref.getFieldName());
            }
            if (ref.getIndex() >= 0) {
                path.append("[").append(ref.getIndex()).append("]");
            }
        }
        return path.length() > 0 ? path.toString() : "unknown";
    }

    /**
     * Walks the cause chain for an IllegalArgumentException — typically the
     * message from an enum's parse() method, which is the most user-helpful.
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
     * Appends a "validValues" entry listing the enum constants.
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
     * Handles validation errors from @Valid (e.g. @NotBlank, @Min, @Max violations).
     * Only fires if JSON deserialization already succeeded.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}
