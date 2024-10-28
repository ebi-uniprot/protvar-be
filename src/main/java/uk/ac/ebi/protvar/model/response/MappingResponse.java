package uk.ac.ebi.protvar.model.response;

import java.util.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.UserInput;

@Setter
@Getter
public class MappingResponse {
	List<UserInput> inputs;
	List<Message> messages;

	public MappingResponse(List<UserInput> inputs) {
		this.inputs = inputs;
		this.messages = new ArrayList<>();
	}
}
