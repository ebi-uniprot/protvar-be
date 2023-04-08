package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Foldx {
	private String proteinAcc;
	private Integer position;
	private String wildType;
	private String mutatedType;
	private Double foldxDdq;
	private Double plddt;
}
