package uk.ac.ebi.protvar.model.score;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.utils.VariantKey;

// Amino acid-level prediction
@JsonInclude(Include.NON_NULL)
@Getter
@Setter
public abstract class Score {
    protected final ScoreType type;
    String acc;
    Integer pos;
    String wt; // not always set e.g. for conserv score
    String mt;
    protected Score(ScoreType type) {
        this.type = type;
    }
    @JsonIgnore
    public String getVariantKey() {
        return VariantKey.protein(type, acc, pos, mt);
    }

    public abstract Score copySubclassFields();
}
