package uk.ac.ebi.protvar.controller.example;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "User registration data")
public class UserDTO {

    @Schema(description = "Unique username", example = "johndoe")
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    @Schema(description = "Valid email address", example = "john@example.com")
    @Email(message = "Email should be valid")
    private String email;

    @Schema(description = "User password", example = "password123")
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;

    @NotNull(message = "Role is required")
    private UserRole role;  // enum field with validation

    /**
     * Optional second role.
     *
     * Important:
     * - JSON `null` for this field is accepted and deserialized as null.
     * - An empty string `""` is NOT accepted and will cause a JSON deserialization error,
     *   because Jackson tries to convert "" to an enum and fails before validation.
     *
     * This is intentional to avoid changing Jackson's global behavior, which may affect other parts of the app.
     */
    private UserRole otherRole;  // optional second role (example)
}