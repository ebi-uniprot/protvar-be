package uk.ac.ebi.protvar.model.response;

import java.util.List;
import java.util.Map;

import lombok.*;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.uniprot.domain.variation.Variant;

@Data
@Builder
@AllArgsConstructor
public class PopulationObservation {
	private String accession;
	private Integer position;
	private String chromosome;
	private Integer genomicPosition;
	private String altBase;
	private List<Variant> variants;
	private Map<String, AlleleFreq> freqMap;
}
