package uk.ac.ebi.uniprot.coordinates.model;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DataServiceCoordinate {
	private String accession;
	private List<DSCGene> gene;
	private List<DSCGnCoordinate> gnCoordinate;
}
