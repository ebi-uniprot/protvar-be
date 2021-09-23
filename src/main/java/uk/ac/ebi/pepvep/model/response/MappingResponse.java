package uk.ac.ebi.pepvep.model.response;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.pepvep.model.UserInput;

@Setter
@Getter
public class MappingResponse {
	List<GenomeProteinMapping> mappings = new ArrayList<>();
	List<UserInput> invalidInputs = new ArrayList<>();
}
