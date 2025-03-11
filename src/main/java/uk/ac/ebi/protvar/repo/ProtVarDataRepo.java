package uk.ac.ebi.protvar.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.data.*;
import uk.ac.ebi.protvar.model.score.Score;

import java.util.List;

public interface ProtVarDataRepo {

	//================================================================================
	// GenomeToProteinMapping
	//================================================================================
	List<GenomeToProteinMapping> getMappingsByChrPos(List<Object[]> chrPosList);
	List<GenomeToProteinMapping> getMappingsByAccPos(List<Object[]> accPosList);


	List<String> getGenInputsByAccession(String accession, Integer page, Integer pageSize);
	Page<UserInput> getGenInputsByAccession(String accession, Pageable pageable);
	Page<UserInput> getGenInputsByEnsemblID(String id, Pageable pageable);

	//================================================================================
	// CADDPrediction
	//================================================================================
	List<CADDPrediction> getCADDByChrPos(List<Object[]> chrPosList);


	//================================================================================
	// Conservation, EVE, ESM1b and AM scores
	//================================================================================

	// Used in PredictionController
	// Does not set the acc, pos and wt values in the Score object (to avoid transmitting
	// unneeded data) in the response.
	List<Score> getScores(String acc, Integer pos, String mt, Score.Name name);

	// Used in MappingFetcher
	// This one needs to set the acc and pos (but not wt) to enable the use of groupBy
	// joinWithDash(name, acc, pos, mt)
	// in building the MappingResponse.
	List<Score> getScores(List<Object[]> accPosList);

	//================================================================================
	// Foldxs, pockets, and protein interactions
	//================================================================================
	List<Foldx> getFoldxs(String accession, Integer position, String variantAA);
	List<Pocket> getPockets(String accession, Integer resid);
	List<Interaction> getInteractions(String accession, Integer resid);
	String getInteractionModel(String a, String b);

}
