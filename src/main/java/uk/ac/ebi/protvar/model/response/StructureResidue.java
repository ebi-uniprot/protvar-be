package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StructureResidue {
    private String experimentalMethod;
    private Double resolution;
    private String pdbId;
    private String chainId;
    private Integer start;
}
