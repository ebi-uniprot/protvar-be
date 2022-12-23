package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Pocket {
	private String structId;
	private Double energy;
	private Double energyPerVol;
	private Double score;
	private List<Integer> residList;

}
