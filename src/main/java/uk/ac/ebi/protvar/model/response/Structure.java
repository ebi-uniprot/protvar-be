package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Structure {
    private String accession;
    private String experimentalMethod;
    private Double resolution;
    private String pdbId;
    private String chainId;
    private List<List<Integer>> observedRegions;
    private Integer start;
    private Integer end;
    private Integer unpStart;
    private Integer unpEnd;
}
