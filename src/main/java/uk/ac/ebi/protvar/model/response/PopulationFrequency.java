package uk.ac.ebi.protvar.model.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.model.Frequency;

@Getter
@Setter
public class PopulationFrequency {
	private String sourceName;
	private List<Frequency> frequencies;
}
