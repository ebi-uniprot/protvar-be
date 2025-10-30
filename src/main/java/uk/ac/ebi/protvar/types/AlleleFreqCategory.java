package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AlleleFreqCategory {
    VERY_RARE(0, "very rare", 0.0, 0.001),           // AF < 0.1%
    RARE(1, "rare", 0.001, 0.005),                    // 0.1% ≤ AF < 0.5%
    LOW_FREQUENCY(2, "low frequency", 0.005, 0.05),   // 0.5% ≤ AF < 5%
    COMMON(3, "common", 0.05, 1.0);                   // AF ≥ 5%

    private final int value;
    private final String label;
    private final double min;
    private final double max;

    public static AlleleFreqCategory parseOrNull(Object input) {
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static AlleleFreqCategory parse(Object input) {
        if (input == null) return null;

        if (input instanceof Integer intVal) {
            for (AlleleFreqCategory category : values()) {
                if (category.value == intVal) {
                    return category;
                }
            }
        } else {
            String str = input.toString().trim();
            // Try case-insensitive enum name match
            for (AlleleFreqCategory category : values()) {
                if (category.name().equalsIgnoreCase(str)) {
                    return category;
                }
            }
            // Try parsing numeric string
            try {
                int intVal = Integer.parseInt(str);
                for (AlleleFreqCategory category : values()) {
                    if (category.value == intVal) {
                        return category;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("Invalid allele frequency category: " + input);
    }

    @JsonCreator
    public static AlleleFreqCategory fromJson(Object input) {
        return parse(input);
    }
}