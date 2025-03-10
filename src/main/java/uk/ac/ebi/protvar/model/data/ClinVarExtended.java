package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
public class ClinVarExtended extends Base {
    private List<String> rcvs;
    String vcv;
    // currently not set but might be used in the future:
    String name;
    String gene;
    String clinicalSig;
    String reviewStatus;
}
