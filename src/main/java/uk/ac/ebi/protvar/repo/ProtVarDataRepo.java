package uk.ac.ebi.protvar.repo;

import uk.ac.ebi.protvar.model.data.*;

import java.util.List;
import java.util.Set;

public interface ProtVarDataRepo {

	//================================================================================
	// GenomeToProteinMapping
	//================================================================================
	List<GenomeToProteinMapping> getMappingsByChrPos(Set<Object[]> chrPosSet);
	List<GenomeToProteinMapping> getMappingsByAccPos(Set<Object[]> accPosList);


	//================================================================================
	// CADDPrediction
	//================================================================================
	List<CADDPrediction> getCADDByChrPos(Set<Object[]> chrPosSet);


	//================================================================================
	// EVEScore
	//================================================================================
	List<EVEScore> getEVEScores(Set<Object[]> accPosSet);

	//================================================================================
	// DBSNP
	//================================================================================
	//List<Dbsnp> getDbsnps(List<String> rsIds);


	//================================================================================
	// Crossmap
	//================================================================================
	List<Crossmap> getCrossmaps(List<Integer> positions, String from);
	List<Crossmap> getCrossmapsByChrPos37(List<Object[]> chrPos37);
	double getPercentageMatch(List<Object[]> chrPosRefList, String ver);


	//================================================================================
	// Pockets, foldxs, and protein interactions
	//================================================================================
	List<Pocket> getPockets(String accession, Integer resid);
	List<Foldx> getFoldxs(String accession, Integer position, String variantAA);
	List<Interaction> getInteractions(String accession, Integer resid);
	String getInteractionModel(String a, String b);

	// Conservation score
	List<ConservScore> getConservScores(String acc, Integer pos);
}
