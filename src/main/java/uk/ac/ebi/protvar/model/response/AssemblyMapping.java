package uk.ac.ebi.protvar.model.response;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.model.grc.Coordinate;

@Getter
@Setter
public class AssemblyMapping {
    String input;
    Coordinate from;
    Coordinate to;
    String error;
}
