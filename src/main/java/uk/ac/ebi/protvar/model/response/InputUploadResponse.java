package uk.ac.ebi.protvar.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "ID of the submitted input.")
public class InputUploadResponse {

    @Schema(description = "Input ID (32-character hex).", example = "148095764c8e06da401a29408e3d19ec")
    private String inputId;

}
