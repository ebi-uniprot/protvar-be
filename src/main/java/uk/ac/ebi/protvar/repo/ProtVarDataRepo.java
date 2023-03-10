package uk.ac.ebi.protvar.repo;

import uk.ac.ebi.protvar.model.grc.Crossmap;
import uk.ac.ebi.protvar.model.response.*;

import java.util.List;
import java.util.Set;

public interface ProtVarDataRepo {

	//================================================================================
	// GenomeToProteinMapping
	//================================================================================

	List<GenomeToProteinMapping> getMappings(String chromosome, Long position);
	List<GenomeToProteinMapping> getMappings(Set<Long> positions);
	List<GenomeToProteinMapping> getMappings(String accession, Long proteinPosition, Set<Integer> codonPositions);
	List<GenomeToProteinMapping> getGenomicCoordsByProteinAccAndPos(List<Object[]> accPPosition);


	//================================================================================
	// CADDPrediction
	//================================================================================
	List<CADDPrediction> getCADDPredictions(Set<Long> positions);


	//================================================================================
	// EVEScore
	//================================================================================
	List<EVEScore> getEVEScores(List<String> accessions, List<Integer> positions);
	List<EVEScore> getEVEScores(List<Object[]> protAccPositions);


	//================================================================================
	// DBSNP
	//================================================================================
	List<Dbsnp> getDbsnps(List<String> rsIds);


	//================================================================================
	// Crossmap
	//================================================================================
	List<Crossmap> getCrossmaps(List<Long> positions, String from);
	List<Crossmap> getCrossmapsByChrPos37(List<Object[]> chrPos37);


	//================================================================================
	// Pockets, foldxs, and protein interactions
	//================================================================================
	List<Pocket> getPockets(String accession, Integer resid);
	List<Foldx> getFoldxs(String accession, Integer position);
	List<Interaction> getInteractions(String accession, Integer resid);
	String getInteractionModel(String a, String b);

	// Conservation score
	List<ConservScore> getConservScores(String acc, Integer pos);
}
