package uk.ac.ebi.protvar.input.params;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.UserInput;

import java.util.List;

@Builder
@Getter
@Setter
public class InputParams {
     String id;
     List<UserInput> inputs;
     /*  id        inputs
      *  --        ------
      *  <id>      cached input - list is populated (retrieved and parsed from cache) using id (,page, pageSize)
      *            also, build & input summary will have been pre-determined/generated
      *  null      non-cached input - input/s is provided in list
      */
     boolean fun; // default false
     boolean pop; // default false
     boolean str; // default false
     String assembly; // default null
     boolean summarise; // default false
     boolean convert; // default false
}
