package uk.ac.ebi.uniprot.proteins.model;

import java.io.Serializable;
import java.util.List;

import lombok.Data;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Data
public class Text implements Serializable {
	private String value;
	private List<Evidence> evidences;
}
