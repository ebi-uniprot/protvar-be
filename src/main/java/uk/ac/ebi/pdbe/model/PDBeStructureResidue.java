package uk.ac.ebi.pdbe.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// PDBe Structure for a UniProt residue range
// Commenting unused fields
public class PDBeStructureResidue {
    String experimental_method;
    //int tax_id;
    double resolution;
    String pdb_id;
    String chain_id;
    //int entity_id;
    int start;
    //int end;
    //int unp_start;
    //int unp_snd;
    //double coverage;
}
