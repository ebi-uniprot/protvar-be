package uk.ac.ebi.uniprot.proteins.model;

import java.util.List;

import lombok.Data;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Data
public class Text {
	private String value;
	private List<Evidence> evidences;
}
