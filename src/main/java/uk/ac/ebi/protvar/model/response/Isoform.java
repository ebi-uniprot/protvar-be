package uk.ac.ebi.protvar.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Builder;
import lombok.Getter;
import uk.ac.ebi.pdbe.model.PDBeStructureResidue;
import uk.ac.ebi.protvar.model.score.*;

import static uk.ac.ebi.protvar.utils.Commons.upperFirstRemainingLower;

@Getter
@Builder
@JsonPropertyOrder({ "accession", "canonical" })
public class Isoform implements Comparable<Isoform> {

	private String accession;
	private boolean canonical;
	private String canonicalAccession;
	private int isoformPosition;
	private String refCodon;
	private int codonPosition;
	private String refAA;
	private String variantAA;
	private String variantCodon;
	private String consequences;
	private String proteinName;
	private List<Transcript> transcripts;

	@JsonInclude(Include.NON_NULL)
	private PopulationObservation populationObservations;
	@JsonInclude(Include.NON_NULL)
	private String populationObservationsUri;

	@JsonInclude(Include.NON_NULL)
	private FunctionalInfo referenceFunction;
	@JsonInclude(Include.NON_NULL)
	private String referenceFunctionUri;

	@JsonInclude(Include.NON_NULL)
	private List<PDBeStructureResidue> proteinStructure;
	@JsonInclude(Include.NON_NULL)
	private String proteinStructureUri;

	@JsonInclude(Include.NON_NULL)
	private ConservScore conservScore;
	@JsonInclude(Include.NON_NULL)
	private AMScore amScore;
	@JsonInclude(Include.NON_NULL)
	private EVEScore eveScore;
	@JsonInclude(Include.NON_NULL)
	private ESMScore esmScore;

	public String getCodonChange(){
		return refCodon + "/" + variantCodon;
	}
	public String getAminoAcidChange(){
		return getRefAA() + "/" + getVariantAA();
	}
	public String getRefAA() {
		return upperFirstRemainingLower(refAA);
	}

	public String getVariantAA() {
		return upperFirstRemainingLower(variantAA);
	}
	@Override
	public int compareTo(Isoform o) {
		return Boolean.compare(o.canonical, this.canonical);
	}
}
