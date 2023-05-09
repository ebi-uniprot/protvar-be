package uk.ac.ebi.uniprot.proteins.api;

import uk.ac.ebi.uniprot.proteins.model.DataServiceProtein;

public interface ProteinsAPI {
    DataServiceProtein[] getProtein(String accessions);
}
