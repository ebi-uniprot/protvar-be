package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Definition
 * When we use interaction we usually mean at the whole protein level.
 * ie protein A interacts with protein B.
 * (protein-protein interaction)
 * The pair together is often called a complex generically or a dimer specifically when
 * it is two proteins (as all our are)
 * The interface is the collection of residues which form the bond between the proteins.
 * We could say that the residue numbers in the summary file form the interface.
 * (The residues do "interact" chemically with each other to fix the two proteins together,
 * however we should use interface and interface residues when talking about the specific
 * amino acids, and interaction when talking about whole proteins)
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
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