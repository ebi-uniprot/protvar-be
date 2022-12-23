package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Interaction {
    private String a;
    private String b;
    private Double pdockq;
    private String modelUrl;

    public Interaction(String a, String b, Double pdockq) {
        this.a = a;
        this.b = b;
        this.pdockq = pdockq;
        this.modelUrl = "/interaction/{a}/{b}/model".replace("{a}", a)
                .replace("{b}", b);
    }
}