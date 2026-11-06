package uk.ac.ebi.protvar.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** Known-variant sources: the bits of coding_variant.known_src (same bits as the stats known union). */
@Getter
@AllArgsConstructor
public enum KnownSource {
    DBSNP(1, "dbSNP"),
    CLINVAR(2, "ClinVar"),
    COSMIC(4, "COSMIC"),
    GNOMAD(8, "gnomAD");

    private final int bit;
    private final String label;

    public static KnownSource parse(String value) {
        if (value == null) return null;
        for (KnownSource s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) return s;
        }
        throw new IllegalArgumentException("Invalid known source: " + value);
    }

    @JsonCreator
    public static KnownSource fromString(String value) {
        return parse(value);
    }

    public static int mask(List<KnownSource> sources) {
        return sources.stream().mapToInt(KnownSource::getBit).reduce(0, (a, b) -> a | b);
    }
}
