package uk.ac.ebi.protvar.model.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GenomeProteinMapping {
	//private String chromosome;
	//private Long geneCoordinateStart;
	//private Long geneCoordinateEnd;
	//private String id;
	//private String userAllele;
	//private String variantAllele;
	private List<Gene> genes;
	//private String input;
	
	//public void setInput(String input) {
	//	this.input = input;
	//}

}
