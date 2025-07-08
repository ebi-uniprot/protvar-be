package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    public static AmClass fromValue(int value) {
        for (AmClass amClass : values()) {
            if (amClass.getValue() == value) {
                return amClass;
            }
        }
        return null;
    }

    @JsonCreator
    public static AmClass fromString(String source) {
        if (source == null) return null;

        switch (source.trim().toLowerCase()) {
            case "ambiguous":
                return AMBIGUOUS;
            case "benign":
                return BENIGN;
            case "pathogenic":
                return PATHOGENIC;
            default:
                throw new IllegalArgumentException("Invalid value '" + source + "' for AmClass");
        }
    }
}
