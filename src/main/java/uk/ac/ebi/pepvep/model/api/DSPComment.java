package uk.ac.ebi.pepvep.model.api;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)

public class DSPComment {
  // WEBRESOURCE MISCELLANEOUS DISEASE FUNCTION SUBUNIT INTERACTION SUBCELLULAR_LOCATION ALTERNATIVE_PRODUCTS
  // TISSUE_SPECIFICITY DEVELOPMENTAL_STAGE INDUCTION DOMAIN PTM
	private String type;
	private List<Text> text;
  //WEBRESOURCE
	private String name;
  //WEBRESOURCE
	private String url;
	private Reaction reaction;
  // INTERACTION
	private List<Interaction> interactions;
  //SUBCELLULAR_LOCATION
	private List<Locations> locations;
  // SUBCELLULAR_LOCATION
  private String molecule;
  //DISEASE
	private Description description;
  //DISEASE
  private String diseaseId;
  //DISEASE
  private String acronym;
  //DISEASE
  private DBReference dbReference;

	public List<Text> getText(Object obj) {
		return Collections.emptyList();
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public List<Text> getText() {
		return text;
	}

	public void setText(Object text) {
    //WEBRESOURCE
		if (text instanceof String) {
			Text t = new Text();
			t.setValue((String) text);
			t.setEvidences(List.of());
			this.text = List.of(t);
		} else {
      //MISCELLANEOUS DISEASE FUNCTION SUBUNIT SUBCELLULAR_LOCATION TISSUE_SPECIFICITY DEVELOPMENTAL_STAGE INDUCTION
      //DOMAIN PTM
			this.text = (List<Text>) text;
		}
	}

	public Reaction getReaction() {
		return reaction;
	}

	public List<Interaction> getInteractions() {
		return interactions;
	}

	public void setInteractions(List<Interaction> interactions) {
		this.interactions = interactions;
	}

	public List<Locations> getLocations() {
		return locations;
	}

	public void setLocations(List<Locations> locations) {
		this.locations = locations;
	}

	public Description getDescription() {
		return description;
	}

	public void setDescription(Description description) {
		this.description = description;
	}
}
