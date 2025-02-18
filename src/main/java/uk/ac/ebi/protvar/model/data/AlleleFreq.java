package uk.ac.ebi.protvar.model.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@Getter
@Setter
@SuperBuilder
public class AlleleFreq extends Base {
    Double af;

    @JsonIgnore
    public String getGroupBy() {
        return String.format("%s-%s",
                Objects.toString(chr, "null"),
                Objects.toString(pos, "null"));
    }
}
