package uk.ac.ebi.protvar.model.score;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.ac.ebi.protvar.utils.Commons;

// Amino acid-level prediction
@JsonInclude(Include.NON_NULL)
@Getter
@NoArgsConstructor
public abstract class Score {

    @AllArgsConstructor
    public enum Name {
        CONSERV("Conservation"), // AAconservation_normalised
        EVE("EVE"), // EVE_score and EVE_class
        ESM("ESM1b"), // ESM1b_score
        AM("AlphaMissense"); // AM_pathogenicity and AM_class

        private String name;
    }

    Score(Name name) {
        this.name = name;
    }

    // no acc, pos and wt
    Score(Name name, String mt) {
        this.name = name;
        this.mt = mt;
    }

    Score(Name name, String acc, Integer pos, String mt) {
        this.name = name;
        this.acc = acc;
        this.pos = pos;
        this.mt = mt;
    }

    Name name;
    String acc;
    Integer pos;
    String wt; // not normally set
    String mt;

    @JsonIgnore
    public String getGroupBy() {
        return Commons.joinWithDash(name, acc, pos, mt);
    }
}
