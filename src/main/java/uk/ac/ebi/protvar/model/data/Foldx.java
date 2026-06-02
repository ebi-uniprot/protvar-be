package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.utils.VariantKey;

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

	public String getVariantKey() {
		return VariantKey.protein(proteinAcc, position, mutatedType);
	}
}
