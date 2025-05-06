package uk.ac.ebi.protvar.types;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CaddCategory {
    // select min(score), max(score) from rel_2025_01_coding_cadd;
    // min	max
    // 0	99
    LIKELY_BENIGN(0.0, 15.0),
    POTENTIALLY_DELETERIOUS(15.0, 20.0),
    QUITE_LIKELY_DELETERIOUS(20.0, 25.0),
    PROBABLY_DELETERIOUS(25.0, 30.0),
    HIGHLY_LIKELY_DELETERIOUS(30.0, 100.0);

    private final double min;
    private final double max;
}