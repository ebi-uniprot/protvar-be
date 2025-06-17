package uk.ac.ebi.protvar.variant.dto;

import lombok.Data;

import java.util.List;

@Data
public class VariantInputRequest {
    private List<String> variants;
}
