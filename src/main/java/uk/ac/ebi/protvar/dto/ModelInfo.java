package uk.ac.ebi.protvar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ModelInfo {
    private String id;
    private String label;
    private String description;
    private boolean defaultModel;
}
