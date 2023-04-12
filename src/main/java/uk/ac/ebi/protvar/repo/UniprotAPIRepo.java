package uk.ac.ebi.protvar.repo;

import java.util.List;

import uk.ac.ebi.protvar.model.PDBeRequest;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.api.DataServiceCoordinate;
import uk.ac.ebi.protvar.model.api.DataServiceProtein;
import uk.ac.ebi.protvar.model.api.DataServiceVariation;
import uk.ac.ebi.protvar.model.response.PDBeStructure;

public interface UniprotAPIRepo {
	
	DataServiceVariation[] getVariationByParam(String parameter, String pathParam);

	DataServiceProtein[] getProtein(String accessions);

	//DataServiceCoordinate[] getGene(UserInput userInput);

	Object[] getPDBe(List<PDBeRequest> requests);

	DataServiceVariation[] getVariationByAccession(String accession, String location);

	String getUniproAccession(String accession);

	DataServiceCoordinate[] getCoordinateByAccession(String accession);

	DataServiceCoordinate[] getCoordinates(String geneName, String chromosome, int offset, int pageSize, String location);

	List<PDBeStructure> getPDBeStructure(String accession, int aaPosition);

}
