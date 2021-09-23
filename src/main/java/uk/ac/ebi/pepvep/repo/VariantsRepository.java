package uk.ac.ebi.pepvep.repo;

import java.util.List;

import uk.ac.ebi.pepvep.model.response.CADDPrediction;
import uk.ac.ebi.pepvep.model.response.GenomeToProteinMapping;

public interface VariantsRepository {

	List<GenomeToProteinMapping> getMappings(String chromosome, Long position);

	List<CADDPrediction> getPredictions(List<Long> positions);

	List<GenomeToProteinMapping> getMappings(List<Long> positions);
}
