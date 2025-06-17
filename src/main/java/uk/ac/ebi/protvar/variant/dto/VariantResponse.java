package uk.ac.ebi.protvar.variant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import uk.ac.ebi.protvar.variant.VariantInput;

@Data
@AllArgsConstructor
public class VariantResponse {
    private VariantInput input;
    private VariantAnnotation annotation;
}
