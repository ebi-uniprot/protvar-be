package uk.ac.ebi.protvar.model.response;

import java.util.*;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.UserInput;

@Setter
@Getter
@AllArgsConstructor
public class MappingResponse {
	//List<GenomeProteinMapping> mappings = new ArrayList<>();
	//List<UserInput> invalidInputs = new ArrayList<>();

	String userInput;
	List<GenomeProteinMapping> mappings = new ArrayList<>();

	//Map<String, List<GenomeProteinMapping>> mappings = new LinkedHashMap<>(); // K=input, V=list of mappings for input

	//List<GenomeProteinMapping> toList() {
	//	return mappings.values()
	//			.stream()
	//			.flatMap(Collection::stream)
	//			.collect(Collectors.toList());
	//}

}
