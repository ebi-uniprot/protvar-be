package uk.ac.ebi.uniprot.variation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Data
public class Feature {
	private String type;
	private String ftId;
	private String alternativeSequence;
	private long begin;
	private long end;
	private List<DSVDbReferenceObject> xrefs = new ArrayList<>();
	private List<Evidence> evidences = new ArrayList<>();
	private String cytogeneticBand;
	private String genomicLocation;
	private String codon;
	private String consequenceType;
	private String wildType;
	private List<Prediction> predictions = new ArrayList<>();
	private List<DSVClinicalSignificance> clinicalSignificances = new ArrayList<>();
	private List<DSVAssociation> association = new ArrayList<>();
	private DSVSourceTypeEnum sourceType;


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
