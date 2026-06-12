package uk.ac.ebi.protvar.model.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Builder;
import lombok.Getter;
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
	private String populationObservationsUri;
	@JsonInclude(Include.NON_NULL)
	private String referenceFunctionUri;
	@JsonInclude(Include.NON_NULL)
	private String proteinStructureUri;

	@JsonInclude(Include.NON_NULL)
	private AmScore amScore;

	@JsonInclude(Include.NON_NULL)
	private PopEveScore popEveScore;

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
		int c = Boolean.compare(o.canonical, this.canonical);   // canonical first
		if (c != 0) return c;
		return compareAccession(this.accession, o.accession);   // then natural accession order
	}

	/** Natural accession order: base accession, then isoform suffix numerically (so -2 sorts before -10). */
	private static int compareAccession(String a, String b) {
		if (a == null || b == null) return a == null ? (b == null ? 0 : -1) : 1;
		String[] pa = a.split("-", 2), pb = b.split("-", 2);
		int base = pa[0].compareTo(pb[0]);
		if (base != 0) return base;
		int na = pa.length > 1 ? safeInt(pa[1]) : 0;
		int nb = pb.length > 1 ? safeInt(pb[1]) : 0;
		if (na != nb) return Integer.compare(na, nb);
		return a.compareTo(b);
	}

	private static int safeInt(String s) {
		try { return Integer.parseInt(s); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
	}
}
