package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class Cosmic extends Base {
    String id;
    String legacyId;
}
