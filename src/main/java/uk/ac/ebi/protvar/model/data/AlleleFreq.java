package uk.ac.ebi.protvar.model.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;
import uk.ac.ebi.protvar.utils.VariantKey;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlleleFreq extends Base {
    Integer ac;
    Integer an;
    Double af;
    @JsonIgnore
    public String getVariantKey() {
        return VariantKey.genomic(chr, pos);
    }

    @JsonIgnore
    public AlleleFreq copySubclassFields() {
        return AlleleFreq.builder()
                .ac(ac)
                .an(an)
                .af(af)
                .build();
    }
}
