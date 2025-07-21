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

    /**
     *
     * @param input the input value to parse
     * @return corresponding AmClass enum or null if input is invalid or null
     */
    public static AmClass parseOrNull(Object input) {
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static AmClass parse(Object input) {
        if (input == null) return null;

        if (input instanceof Integer intVal) {
            for (AmClass amClass : values()) {
                if (amClass.value == intVal) {
                    return amClass;
                }
            }
        } else {
            String str = input.toString().trim();
            // Try case-insensitive enum name match
            for (AmClass amClass : values()) {
                if (amClass.name().equalsIgnoreCase(str)) {
                    return amClass;
                }
            }
            // Try parsing numeric string
            try {
                int intVal = Integer.parseInt(str);
                for (AmClass amClass : values()) {
                    if (amClass.value == intVal) {
                        return amClass;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("Invalid AlphaMissense class: " + input);
    }

    @JsonCreator
    public static AmClass fromJson(Object input) {
        return parse(input);
    }
}
