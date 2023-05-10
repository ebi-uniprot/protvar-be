package uk.ac.ebi.uniprot.variation.api;

import uk.ac.ebi.uniprot.variation.model.DataServiceVariation;

public interface VariationAPI {
    DataServiceVariation[] getVariationByParam(String parameter, String pathParam);

    DataServiceVariation[] getVariation(String accession, int location);

    DataServiceVariation[] getVariation(String accessions);

    DataServiceVariation[] getVariationAccessionLocations(String accLocs);
}
