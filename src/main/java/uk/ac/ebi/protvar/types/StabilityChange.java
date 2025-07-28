package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StabilityChange {
    LIKELY_DESTABILISING,
    UNLIKELY_DESTABILISING;

    public static StabilityChange parse(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        for (StabilityChange c : values()) {
            if (c.name().equalsIgnoreCase(trimmed)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Invalid StabilityChange category: " + value);
    }

    /**
     * Enables case-insensitive deserialization from JSON.
     * Used only for JSON (@RequestBody) via Jackson.
     * For query params (@RequestParam), see the Converter.
     */
    @JsonCreator
    public static StabilityChange fromString(String value) {
        return parse(value);
    }
}
