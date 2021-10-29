package uk.ac.ebi.protvar.utils;

public enum ProteinType2Description {

	SIGNAL("SIGNAL", "Signal Peptide"),
	PROPEP("PROPEP", "Propeptide"),
	CHAIN("CHAIN", "Chain"),
	DOMAIN("DOMAIN", "Functional Domain"),
	ACT_SITE("ACT_SITE", "Active Site Residue"),
	METAL("METAL", "Metal Ion Binding Site Residue"),
	SITE("SITE", "Functionally Important Residue"),
	MOD_RES("MOD_RES", "PTM Modified Residue"),
	CARBOHYD("CARBOHYD", "PTM Carbohydrate"),
	DISULFID("DISULFID", "PTM Disulfide Bond Residue"),
	MUTAGEN("MUTAGEN", "Mutated Residue"),
	INIT_MET("INIT_MET", "Cleaved Initiator Methionine"),
	TRANSIT("TRANSIT", "Cleaved Transit Peptide"),
	TOPO_DOM("TOPO_DOM", "Transmembrane Protein Topological Region"),
	TRANSMEM("TRANSMEM", "Helical Transmembrane Peptide"),
	REPEAT("REPEAT", "Repeated Sequence"),
	CA_BIND("CA_BIND", "Calcium Binding Residue"),
	ZN_FING("ZN_FING", "Zinc Finger Residue"),
	DNA_BIND("DNA_BIND", "DNA Binding Residue"),
	NP_BIND("NP_BIND", "Nucleotide Phosphate Binding Residue"),
	COILED("COILED", "Coiled-coil Region"),
	MOTIF("MOTIF", "Functional Motif"),
	COMPBIAS("COMPBIAS", "AA Composition Bias"),
	BINDING("BINDING", "Binding Site Residue"),
	NON_STD("NON_STD", "Non-standard Amino Acid"),
	LIPID("LIPID", "PTM bound Lipid"),
	CROSSLINK("CROSSLINK", "Covalent Link To Another Protein"),
	CONFLICT("CONFLICT", "Difference In Reported Protein Sequences"),
	HELIX("HELIX", "Alpha-helix"),
	STRAND("STRAND", "Beta-strand"),
	PEPTIDE("PEPTIDE", "Peptide"),
	REGION("REGION", "Region");
	
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
