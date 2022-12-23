package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Pdockq {
	private String pair;
	private Double pdockq;

}
