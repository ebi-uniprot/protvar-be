package uk.ac.ebi.pepvep.model.api;

public enum DSVSourceTypeEnum {
    large_scale_study("large scale study"), uniprot("uniprot"), mixed("mixed");
    private String sourceName;
    DSVSourceTypeEnum(String sourceName) {
        this.sourceName = sourceName;
    }
   
}

