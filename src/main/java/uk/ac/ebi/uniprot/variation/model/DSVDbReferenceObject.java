package uk.ac.ebi.uniprot.variation.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class DSVDbReferenceObject implements Serializable {
    private String name;
    private String id;
    private String url;
    private String alternativeUrl;
    private Boolean reviewed;
}

