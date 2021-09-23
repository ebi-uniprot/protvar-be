package uk.ac.ebi.pepvep.model.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

@Data
public class Feature {
	private long begin;
	private long end;
	private String cytogeneticBand;
	private String type;
	private String ftId;
	private String codon;
	private List<Prediction> predictions = new ArrayList<>();
	private String wildType;
	private String alternativeSequence;
	private List<DSVDbReferenceObject> xrefs = new ArrayList<>();
	private List<Evidence> evidences = new ArrayList<>();
	private DSVSourceTypeEnum sourceType;
	private String genomicLocation;
	private String consequenceType;
	
	private List<DSVAssociation> association = new ArrayList<>();
	private List<DSVClinicalSignificance> clinicalSignificances = new ArrayList<>();
	private List<DSVPopulationFrequency> populationFrequencies = new ArrayList<>();

	public String getSignificances() {
		String clincalSignificances = "";
		if (this.clinicalSignificances != null) {
			clincalSignificances = this.clinicalSignificances.stream().map(DSVClinicalSignificance::getType)
					.collect(Collectors.joining(","));
		}
		return clincalSignificances;
	}
}
