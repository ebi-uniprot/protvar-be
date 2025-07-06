package uk.ac.ebi.protvar.model.score;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ScoreType {
    CONSERV("Conservation"),        // AAconservation_normalised
    EVE("EVE"),                     // EVE_score and EVE_class
    ESM("ESM1b"),                   // ESM1b_score
    AM("AlphaMissense");            // AM_pathogenicity and AM_class
    private final String name;
}
