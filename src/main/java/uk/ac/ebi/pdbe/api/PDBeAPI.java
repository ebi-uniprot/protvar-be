package uk.ac.ebi.pdbe.api;

import uk.ac.ebi.pdbe.model.PDBeStructureResidue;

import java.util.List;

public interface PDBeAPI {
    List<PDBeStructureResidue> get(String accession, int position);

}
