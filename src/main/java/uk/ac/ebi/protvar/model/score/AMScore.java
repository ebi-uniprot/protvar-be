package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AMScore extends Score {
    private Double amPathogenicity;
    private AMClass amClass;

    public AMScore(Double score, AMClass amClass) {
        super(Name.AM);
        this.amPathogenicity = score;
        this.amClass = amClass;
    }

    public AMScore(String mt, Double score, Integer amClass) {
        super(Name.AM, mt);
        this.amPathogenicity = score;
        this.amClass = AMClass.fromNum(amClass);
    }

    public AMScore(String acc, Integer pos, String mt, Double score, Integer amClass) {
        super(Name.AM, acc, pos, mt);
        this.amPathogenicity = score;
        this.amClass = AMClass.fromNum(amClass);
    }

    public AMScore copy() {
        return new AMScore(amPathogenicity, amClass);
    }
}
