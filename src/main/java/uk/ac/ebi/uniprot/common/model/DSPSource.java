package uk.ac.ebi.uniprot.common.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class DSPSource implements Serializable {
	private String name;
	private String id;
	private String url;
	private String alternativeUrl;

}
