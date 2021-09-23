package uk.ac.ebi.pepvep.model.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataServiceVariation {
    private String accession;
    private String geneName;
    private List<Feature> features;
    
}
