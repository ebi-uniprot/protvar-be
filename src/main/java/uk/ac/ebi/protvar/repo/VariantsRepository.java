package uk.ac.ebi.protvar.repo;

import java.util.List;

import uk.ac.ebi.protvar.model.response.CADDPrediction;
import uk.ac.ebi.protvar.model.response.GenomeToProteinMapping;

public interface VariantsRepository {

	List<GenomeToProteinMapping> getMappings(String chromosome, Long position);

	List<CADDPrediction> getPredictions(List<Long> positions);

	List<GenomeToProteinMapping> getMappings(List<Long> positions);
}
