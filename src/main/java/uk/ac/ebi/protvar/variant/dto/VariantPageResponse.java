package uk.ac.ebi.protvar.variant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class VariantPageResponse {
    private List<VariantResponse> results;
    private int total;
    private int page;
    private int size;
    private int totalPages;
}
