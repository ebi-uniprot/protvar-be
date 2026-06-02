package uk.ac.ebi.protvar.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Response containing the input ID generated after upload.")
public class InputUploadResponse {

    @Schema(description = "The unique ID assigned to the uploaded input.", example = "abc123xyz")
    private String inputId;

}
