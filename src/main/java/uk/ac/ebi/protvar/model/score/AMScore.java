package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AMScore extends Score {
    private Double amPathogenicity;
    private AMClass amClass;

    public AMScore(String acc, Integer pos, String mt, Double score,
                   Integer amClass) {
        super(Name.AM, acc, pos, null, mt);
        this.amPathogenicity = score;
        this.amClass = AMClass.fromNum(amClass);
    }

}
