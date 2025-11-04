package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PopEveClass {
    SEVERE(0, "severe", Double.NEGATIVE_INFINITY, -5.056),
    MODERATELY_DELETERIOUS(1, "moderately deleterious", -5.056, -4.617),
    UNLIKELY_DELETERIOUS(2, "unlikely deleterious", -4.617, Double.POSITIVE_INFINITY);

    private final int value;
    private final String label;
    private final double min;
    private final double max;

    /**
     * Parse input to PopEveClass, returning null if invalid
     * @param input the input value to parse
     * @return corresponding PopEveClass enum or null if input is invalid or null
     */
    public static PopEveClass parseOrNull(Object input) {
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parse input to PopEveClass
     * @param input the input value to parse (Integer, String enum name, or numeric string)
     * @return corresponding PopEveClass enum
     * @throws IllegalArgumentException if input is invalid
     */
    public static PopEveClass parse(Object input) {
        if (input == null) return null;

        if (input instanceof Integer intVal) {
            for (PopEveClass popEveClass : values()) {
                if (popEveClass.value == intVal) {
                    return popEveClass;
                }
            }
        } else {
            String str = input.toString().trim();
            // Try case-insensitive enum name match
            for (PopEveClass popEveClass : values()) {
                if (popEveClass.name().equalsIgnoreCase(str)) {
                    return popEveClass;
                }
            }
            // Try parsing numeric string
            try {
                int intVal = Integer.parseInt(str);
                for (PopEveClass popEveClass : values()) {
                    if (popEveClass.value == intVal) {
                        return popEveClass;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("Invalid PopEVE class: " + input);
    }

    @JsonCreator
    public static PopEveClass fromJson(Object input) {
        return parse(input);
    }
}