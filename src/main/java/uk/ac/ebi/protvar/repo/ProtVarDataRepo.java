package uk.ac.ebi.protvar.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.data.*;
import uk.ac.ebi.protvar.model.score.EVEScore;
import uk.ac.ebi.protvar.model.score.Score;

import java.util.List;
import java.util.Set;

public interface ProtVarDataRepo {

	//================================================================================
	// GenomeToProteinMapping
	//================================================================================
	List<GenomeToProteinMapping> getMappingsByChrPos(Set<Object[]> chrPosSet);
	List<GenomeToProteinMapping> getMappingsByAccPos(Set<Object[]> accPosList);


	Page<UserInput> getGenInputsByAccession(String accession, Pageable pageable);

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
	// Conservation, EVE, ESM1b and AM scores
	//================================================================================
	List<Score> getScores(String acc, Integer pos, String mt, Score.Name name);

	//================================================================================
	// Foldxs, pockets, and protein interactions
	//================================================================================
	List<Foldx> getFoldxs(String accession, Integer position, String variantAA);
	List<Pocket> getPockets(String accession, Integer resid);
	List<Interaction> getInteractions(String accession, Integer resid);
	String getInteractionModel(String a, String b);

}
