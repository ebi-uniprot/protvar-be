package uk.ac.ebi.uniprot.proteins.model;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class Locations implements Serializable {
	private Location location;
	private Location topology;
}
