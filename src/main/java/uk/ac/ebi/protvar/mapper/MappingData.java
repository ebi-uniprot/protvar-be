package uk.ac.ebi.protvar.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import uk.ac.ebi.protvar.model.data.CaddPrediction;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.record.ArrayPair;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
public class MappingData {
    private final ArrayPair<String, Integer> chrPosArrays;
    private final Map<String, List<GenomeToProteinMapping>> g2pMap;
    private final Map<String, List<CaddPrediction>> caddMap;
    private final ArrayPair<String, Integer> accPosArrays;
    private final Set<String> canonicalAccessions;
    private final Map<String, List<Score>> amScoreMap;
}
