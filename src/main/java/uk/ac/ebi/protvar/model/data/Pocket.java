package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Pocket {
	private String structId;
	private Integer pocketId;
	private Double radGyration;
	private Double energyPerVol;
	private Double buriedness;
	private List<Integer> resid;
	private Double meanPlddt;
	private Double score;
}
