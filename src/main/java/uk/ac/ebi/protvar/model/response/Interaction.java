package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Interaction {
    private String a;
    private List<Integer> aresidues;
    private String b;
    private List<Integer> bresidues;
    private Double pdockq;
    private String pdbModel;

    public Interaction(String a, List<Integer> aresidues, String b, List<Integer> bresidues, Double pdockq) {
        this.a = a;
        this.aresidues = aresidues;
        this.b = b;
        this.bresidues = bresidues;
        this.pdockq = pdockq;
        this.pdbModel = "/interaction/{a}/{b}/model".replace("{a}", a)
                .replace("{b}", b);
    }
}