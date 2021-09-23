package uk.ac.ebi.pepvep.model.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PopulationObservation {

	private List<Variation> genomicColocatedVariant;
	private List<Variation> proteinColocatedVariant;
}
