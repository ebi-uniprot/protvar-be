package uk.ac.ebi.pepvep.model;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Gene implements Cloneable {
	private String chromosome;
	private long start;
	private long end;
	private String ensgId;
	private String enstId;
	private String hgvsg;
	private String hgvsp;
	private String symbol;
	private String source;
	private String hgncId;
	private String allele;
	private String exon;
	private int strand;
	private boolean hasENSP;
	private boolean hasENST;
	private List<String> otherTranscripts;
	private List<String> otherTranslations;

	@Override
	public Gene clone() {
		Gene clonedGene = new Gene();
		clonedGene.setChromosome(this.getChromosome());
		clonedGene.setEnd(this.getEnd());
		clonedGene.setEnsgId(this.getEnsgId());
		clonedGene.setEnstId(this.getEnstId());
		clonedGene.setExon(this.getExon());
		clonedGene.setHasENSP(this.isHasENSP());
		clonedGene.setHasENST(this.isHasENST());
		clonedGene.setHgvsp(this.getHgvsp());
		clonedGene.setOtherTranscripts(this.getOtherTranscripts());
		clonedGene.setOtherTranslations(this.getOtherTranslations());
		clonedGene.setSource(this.getSource());
		clonedGene.setStart(this.getStart());
		clonedGene.setStrand(this.getStrand());
		clonedGene.setSymbol(this.getSymbol());
		return clonedGene;
	}
}
