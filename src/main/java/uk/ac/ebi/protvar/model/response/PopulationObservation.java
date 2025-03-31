package uk.ac.ebi.protvar.model.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.uniprot.domain.variation.Variant;

@Getter
@Setter
@AllArgsConstructor
public class PopulationObservation {
	private List<Variant> variants;
}
