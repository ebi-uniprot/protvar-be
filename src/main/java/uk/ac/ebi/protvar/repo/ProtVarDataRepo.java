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
	List<GenomeToProteinMapping> getMappings(List<Long> positions);
	List<GenomeToProteinMapping> getMappings(String accession, Long proteinPosition, Set<Integer> codonPositions);


	//================================================================================
	// CADDPrediction
	//================================================================================
	List<CADDPrediction> getCADDPredictions(List<Long> positions);


	//================================================================================
	// EVEScore
	//================================================================================
	List<EVEScore> getEVEScores(List<String> accessions, List<Integer> positions);


	//================================================================================
	// DBSNP
	//================================================================================
	List<Dbsnp> getDbsnps(List<String> rsIds);


	//================================================================================
	// Crossmap
	//================================================================================
	List<Crossmap> getCrossmaps(List<Long> positions, String from);


	//================================================================================
	// Pockets, foldxs, and protein interactions
	//================================================================================
	List<Pocket> getPockets(String accession, Integer resid);
	List<Foldx> getFoldxs(String accession, Integer position);
	List<Interaction> getInteractions(String accession, Integer resid);
	String getInteractionModel(String a, String b);
}
