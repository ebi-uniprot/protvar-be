package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StabilityChange {
    LIKELY_DESTABILISING,
    UNLIKELY_DESTABILISING;

    /**
     * Safe parsing that returns null on invalid input
     */
    public static StabilityChange parseOrNull(Object input) {
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a StabilityChange from string (case-insensitive).
     * Shared by both JSON deserialization and Spring's Converter.
     */
    public static StabilityChange parse(Object input) {
        if (input == null) return null;

        String value = input.toString().trim();
        for (StabilityChange c : values()) {
            if (c.name().equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Invalid stability change: " + input);
    }

    /**
     * Enables case-insensitive deserialization from JSON.
     * Used only for JSON (@RequestBody) via Jackson.
     */
    @JsonCreator
    public static StabilityChange fromJson(Object input) {
        return parse(input);
    }
}
