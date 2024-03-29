package uk.ac.ebi.uniprot.variation.model;

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
