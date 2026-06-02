package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import uk.ac.ebi.protvar.types.AmClass;

@Getter
public class AmScore extends Score {
    private Double amPathogenicity;
    private AmClass amClass;


    // Full constructor
    public AmScore(String acc, Integer pos, String mt, Double score, AmClass amClass) {
        super(ScoreType.AM);
        this.acc = acc;
        this.pos = pos;
        this.mt = mt;
        this.amPathogenicity = score;
        this.amClass = amClass;
    }

    // Minimal constructor
    public AmScore(String mt, Double score, AmClass amClass) {
        super(ScoreType.AM);
        this.mt = mt;
        this.amPathogenicity = score;
        this.amClass = amClass;
    }

    @Override
    public AmScore copySubclassFields() {
        return new AmScore(null, amPathogenicity, amClass);
    }
}
