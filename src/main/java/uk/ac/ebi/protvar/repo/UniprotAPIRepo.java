package uk.ac.ebi.protvar.repo;

import uk.ac.ebi.protvar.model.api.DataServiceCoordinate;
import uk.ac.ebi.protvar.model.api.DataServiceProtein;
import uk.ac.ebi.protvar.model.api.DataServiceVariation;

public interface UniprotAPIRepo {
	
	DataServiceVariation[] getVariationByParam(String parameter, String pathParam);

	DataServiceProtein[] getProtein(String accessions);

	//DataServiceCoordinate[] getGene(UserInput userInput);


	DataServiceVariation[] getVariationByAccession(String accession, String location);


	DataServiceCoordinate[] getCoordinateByAccession(String accession);

	DataServiceCoordinate[] getCoordinates(String geneName, String chromosome, int offset, int pageSize, String location);

}
