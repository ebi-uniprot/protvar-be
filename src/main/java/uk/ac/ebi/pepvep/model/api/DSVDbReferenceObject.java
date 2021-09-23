package uk.ac.ebi.pepvep.model.api;

import lombok.Getter;

@Getter
public class DSVDbReferenceObject {
    private String name;
    private String id;
    private String url;
    private String alternativeUrl;
    private Boolean reviewed;
}

