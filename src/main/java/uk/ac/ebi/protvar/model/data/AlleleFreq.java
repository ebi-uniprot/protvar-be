package uk.ac.ebi.protvar.model.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AlleleFreq extends Base {
    Integer ac;
    Integer an;
    Double af;

    @JsonIgnore
    public String getGroupBy() {
        return String.format("%s-%s",
                Objects.toString(chr, "null"),
                Objects.toString(pos, "null"));
    }

    @Builder
    public static class GnomadFreq { // with only required fields in API response
        Integer ac;
        Integer an;
        Double af;
    }

    public GnomadFreq toGnomadFreq() {
        return GnomadFreq.builder()
                .ac(ac)
                .an(an)
                .af(af)
                .build();
    }
}
