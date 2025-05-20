package uk.ac.ebi.protvar.model.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import uk.ac.ebi.protvar.model.data.AlleleFreq;

@Getter
@Builder
public class Gene {
	private String ensg;
	private boolean reverseStrand;
	private String geneName;
	private String refAllele;
	private String altAllele;
	private List<Isoform> isoforms;
	private Double caddScore;
	private AlleleFreq.GnomadFreq gnomadFreq;
}
