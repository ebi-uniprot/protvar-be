package uk.ac.ebi.protvar.model.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Gene {
	private String ensg;
	private boolean reverseStrand;
	private String geneName;
	private String refAllele;
	private String altAllele;
	private List<IsoFormMapping> isoforms;
	private Double caddScore;
}
