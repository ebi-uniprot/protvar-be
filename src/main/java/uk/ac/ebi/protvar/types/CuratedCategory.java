package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * UniProt curated variant (humsavar) categories: the bits of coding_variant.humsavar. ANY = in humsavar at all.
 * A variant can have several (the category is per disease).
 */
@Getter
@AllArgsConstructor
public enum CuratedCategory {
    ANY(7),
    LP_P(1),
    LB_B(2),
    US(4);

    private final int bit;

    public static CuratedCategory parse(String value) {
        if (value == null) return null;
        String v = value.trim().replace('/', '_');
        for (CuratedCategory c : values()) {
            if (c.name().equalsIgnoreCase(v)) return c;
        }
        throw new IllegalArgumentException("Invalid curated category: " + value);
    }

    @JsonCreator
    public static CuratedCategory fromString(String value) {
        return parse(value);
    }

    public static int mask(List<CuratedCategory> categories) {
        return categories.stream().mapToInt(CuratedCategory::getBit).reduce(0, (a, b) -> a | b);
    }
}
