package uk.ac.ebi.protvar.utils;

/**
 * Probably out-dated
 * Refer to `uk.ac.ebi.protvar.common.model.FeatureType`
 *
 * Differences from the FeatureType enum:
 * - Included in this enum, but not in FeatureType: CA_BIND, CROSSLINK, METAL, NP_BIND
 * - Included in FeatureType, but not in this enum: CROSSLNK, INTRAMEM, NON_CONS, NON_TER, TURN, UNSURE, VAR_SEQ, VARIANT
 *
 * Need to move to frontend where it is used.
 */
public enum ProteinType2Description {

	ACT_SITE("ACT_SITE", "Active Site Residue"),
	BINDING("BINDING", "Binding Site Residue"),
	CA_BIND("CA_BIND", "Calcium Binding Residue"),
	CARBOHYD("CARBOHYD", "PTM Carbohydrate"),
	CHAIN("CHAIN", "Chain"),
	COILED("COILED", "Coiled-coil Region"),
	COMPBIAS("COMPBIAS", "AA Composition Bias"),
	CONFLICT("CONFLICT", "Difference In Reported Protein Sequences"),
	CROSSLINK("CROSSLINK", "Covalent Link To Another Protein"),
	DISULFID("DISULFID", "PTM Disulfide Bond Residue"),
	DNA_BIND("DNA_BIND", "DNA Binding Residue"),
	DOMAIN("DOMAIN", "Functional Domain"),
	HELIX("HELIX", "Alpha-helix"),
	INIT_MET("INIT_MET", "Cleaved Initiator Methionine"),
	LIPID("LIPID", "PTM bound Lipid"),
	METAL("METAL", "Metal Ion Binding Site Residue"),
	MOD_RES("MOD_RES", "PTM Modified Residue"),
	MOTIF("MOTIF", "Functional Motif"),
	MUTAGEN("MUTAGEN", "Mutated Residue"),
	NON_STD("NON_STD", "Non-standard Amino Acid"),
	NP_BIND("NP_BIND", "Nucleotide Phosphate Binding Residue"),
	PEPTIDE("PEPTIDE", "Peptide"),
	PROPEP("PROPEP", "Propeptide"),
	REGION("REGION", "Region"),
	REPEAT("REPEAT", "Repeated Sequence"),
	SIGNAL("SIGNAL", "Signal Peptide"),
	SITE("SITE", "Functionally Important Residue"),
	STRAND("STRAND", "Beta-strand"),
	TOPO_DOM("TOPO_DOM", "Transmembrane Protein Topological Region"),
	TRANSIT("TRANSIT", "Cleaved Transit Peptide"),
	TRANSMEM("TRANSMEM", "Helical Transmembrane Peptide"),
	ZN_FING("ZN_FING", "Zinc Finger Residue");
	
	ProteinType2Description(String oneLetter, String description) {
		this.type = oneLetter;
		this.description = description;
	}

	private final String type;
	private final String description;
	
	public static String getDescription(String type){
        for(ProteinType2Description typeVal : ProteinType2Description.values()){
           if(type.equals(typeVal.type))
               return typeVal.description;
        }
       return null;
    }
}
