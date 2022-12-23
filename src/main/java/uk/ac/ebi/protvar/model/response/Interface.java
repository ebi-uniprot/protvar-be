package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Interface {
	private String protein;
	private String chain;
	private String pair;
	private List<Integer> residues;

}
