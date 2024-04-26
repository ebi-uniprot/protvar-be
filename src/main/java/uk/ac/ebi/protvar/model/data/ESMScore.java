package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


@AllArgsConstructor
@Getter
@Setter
public class ESMScore {
	private String accession;
	private Integer position;
	private String mtAA;
	private Double score;
}
