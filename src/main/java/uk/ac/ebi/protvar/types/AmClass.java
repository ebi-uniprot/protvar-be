package uk.ac.ebi.protvar.types;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AmClass { // TODO use existing AMClass enum
    // select distinct am_class from alphamissense;
    // am_class
    // 0
    // 1
    // -1
    AMBIGUOUS(-1),
    BENIGN(0),
    PATHOGENIC(1);

    private final int value;
}
