package uk.ac.ebi.protvar.model.score;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Getter;

// Amino acid-level prediction
@JsonInclude(Include.NON_NULL)
@Getter
public abstract class Score {

    @AllArgsConstructor
    public enum Name {
        CONSERV("Conservation"), // AAconservation_normalised
        EVE("EVE"), // EVE_score and EVE_class
        ESM("ESM1b"), // ESM1b_score
        AM("AlphaMissense"); // AM_pathogenicity and AM_class

        private String name;
    }

    Score(Name name, String acc, Integer pos, String wt, String mt) {
        this.name = name;
        this.acc = acc;
        this.pos = pos;
        this.wt = wt;
        this.mt = mt;
    }

    Name name;
    String acc;
    Integer pos;
    String wt;
    String mt;

}
