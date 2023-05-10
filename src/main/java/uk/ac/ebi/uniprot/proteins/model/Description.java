package uk.ac.ebi.uniprot.proteins.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Getter
public class Description implements Serializable {
	private String value;
	private List<Evidence> evidences;
}
