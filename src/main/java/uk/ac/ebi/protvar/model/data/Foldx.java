package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Foldx {
	private String proteinAcc;
	private Integer position;
	private String afId; // AF Fragment ID
	private Integer afPos; // AF Fragment Position
	private String wildType;
	private String mutatedType;
	private Double foldxDdg; // FoldX_ddG
	private Double plddt; // AlphaFold_pLDDT
	private int numFragments = 1; // default

	public String getGroupBy() {
		return String.format("%s-%s-%s",
				Objects.toString(proteinAcc, "null"),
				Objects.toString(position, "null"),
				Objects.toString(mutatedType, "null"));
	}
}
