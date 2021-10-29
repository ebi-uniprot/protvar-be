package uk.ac.ebi.protvar.model.response;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.model.api.DSVAssociation;
import uk.ac.ebi.protvar.model.api.DSVClinicalSignificance;
import uk.ac.ebi.protvar.model.api.DSVDbReferenceObject;
import uk.ac.ebi.protvar.model.api.DSVPopulationFrequency;
import uk.ac.ebi.protvar.model.api.Evidence;
import uk.ac.ebi.protvar.model.api.Prediction;

@Getter
@Setter
public class Variation {
	@JsonIgnore
	private long begin;
	@JsonIgnore
	private long end;
	
	private String cytogeneticBand;

	// reference AA , can't be null and should always be proper AA not -
	@JsonInclude(Include.NON_NULL)
	private String wildType;
	//to AA, can't be null and should always be proper AA not -
	@JsonInclude(Include.NON_NULL)
	private String alternativeSequence;
	//should be number
	//greater than 0
	//never be null
	private String genomicLocation;

	@JsonInclude(Include.NON_NULL)
	private List<Variation> colocatedVariants;
	private List<DSVPopulationFrequency> populationFrequencies = new ArrayList<>();

	private List<Prediction> predictions = new ArrayList<>();
	private List<DSVDbReferenceObject> xrefs = new ArrayList<>();
	private List<Evidence> evidences = new ArrayList<>();
	private List<DSVAssociation> association = new ArrayList<>();
	private List<DSVClinicalSignificance> clinicalSignificances = new ArrayList<>();

}