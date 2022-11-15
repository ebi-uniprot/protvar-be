package uk.ac.ebi.protvar.repo;

import uk.ac.ebi.protvar.model.grc.Crossmap;
import uk.ac.ebi.protvar.model.response.CADDPrediction;
import uk.ac.ebi.protvar.model.response.EVEScore;
import uk.ac.ebi.protvar.model.response.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Variant;

import java.util.List;
import java.util.Set;

public interface VariantsRepository {

	List<GenomeToProteinMapping> getMappings(String chromosome, Long position);

	List<CADDPrediction> getPredictions(List<Long> positions);
	List<GenomeToProteinMapping> getMappings(List<Long> positions);
	List<GenomeToProteinMapping> getMappings(String accession, Long proteinPosition, Set<Integer> codonPositions);

	List<EVEScore> getEVEScores(List<String> accessions, List<Integer> positions);

	List<Variant> getVariants(List<String> rsIds);
	List<Crossmap> getCrossmaps(List<Long> positions, String from);
}
