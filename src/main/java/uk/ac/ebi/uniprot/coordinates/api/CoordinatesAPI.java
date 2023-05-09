package uk.ac.ebi.uniprot.coordinates.api;

import uk.ac.ebi.uniprot.coordinates.model.DataServiceCoordinate;

public interface CoordinatesAPI {
    DataServiceCoordinate[] getCoordinateByAccession(String accession);

    DataServiceCoordinate[] getCoordinates(String geneName, String chromosome, int offset, int pageSize, String location);
}
