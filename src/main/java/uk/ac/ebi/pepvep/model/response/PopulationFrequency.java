package uk.ac.ebi.pepvep.model.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.pepvep.model.Frequency;

@Getter
@Setter
public class PopulationFrequency {
	private String sourceName;
	private List<Frequency> frequencies;
}
