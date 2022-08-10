package uk.ac.ebi.protvar.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Builder;
import lombok.Getter;
import uk.ac.ebi.protvar.model.api.ProteinFeature;

import static uk.ac.ebi.protvar.utils.Commons.upperFirstRemainingLower;

@Getter
@Builder
@JsonPropertyOrder({ "accession", "canonical" })
public class IsoFormMapping implements Comparable<IsoFormMapping> {

	private String accession;
	private boolean canonical;
	private String canonicalAccession;
	private int isoformPosition;
	private String refCodon;
	//TODO clean up
	//private String userCodon;
	private int cdsPosition;
	private String refAA;
	//TODO clean up
	//private String userAA;
	private String variantAA;
	private String variantCodon;
	private String consequences;
	private String proteinName;
	private List<Ensp> translatedSequences;

	@JsonInclude(Include.NON_NULL)
	private PopulationObservation populationObservations;
	@JsonInclude(Include.NON_NULL)
	private String populationObservationsUri;

	@JsonInclude(Include.NON_NULL)
	private Protein referenceFunction;
	@JsonInclude(Include.NON_NULL)
	private String referenceFunctionUri;

	//@JsonInclude(Include.NON_NULL)
	//private List<ProteinFeature> experimentalEvidence;

	//@JsonInclude(Include.NON_NULL)
	//private EvolutionInference evolutionalInference;
	//@JsonInclude(Include.NON_NULL)
	//private String evolutionalInferenceUri;

	@JsonInclude(Include.NON_NULL)
	private List<PDBeStructure> proteinStructure;
	@JsonInclude(Include.NON_NULL)
	private String proteinStructureUri;

	@JsonInclude(Include.NON_NULL)
	private Double eveScore;

	@JsonInclude(Include.NON_NULL)
	private Integer eveClass;

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
	public int compareTo(IsoFormMapping o) {
		return Boolean.compare(o.canonical, this.canonical);
	}
}
