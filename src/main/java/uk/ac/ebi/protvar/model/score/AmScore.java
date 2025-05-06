package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.types.AmClass;

@Getter
@Setter
@NoArgsConstructor
public class AmScore extends Score {
    private Double amPathogenicity;
    private AmClass amClass;

    public AmScore(Double score, AmClass amClass) {
        super(Name.AM);
        this.amPathogenicity = score;
        this.amClass = amClass;
    }

    public AmScore(String mt, Double score, Integer value) {
        super(Name.AM, mt);
        this.amPathogenicity = score;
        this.amClass = AmClass.fromValue(value);
    }

    public AmScore(String acc, Integer pos, String mt, Double score, Integer value) {
        super(Name.AM, acc, pos, mt);
        this.amPathogenicity = score;
        this.amClass = AmClass.fromValue(value);
    }

    public AmScore copy() {
        return new AmScore(amPathogenicity, amClass);
    }
}
