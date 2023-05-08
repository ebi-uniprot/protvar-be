package uk.ac.ebi.pdbe.model;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.pdbe.model.ObservedRegion;

@Getter
@Setter
// PDBe Structure for a UniProt accession
public class PDBeStructure {
    String experimental_method;
    int tax_id;
    double resolution;
    String pdb_id;
    String chain_id;
    int entity_id;
    int preferred_assembly_id;
    ObservedRegion[] observed_regions;
    int start;
    int end;
    int unp_start;
    int unp_snd;
    double coverage;
}
