package uk.ac.ebi.protvar.mapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.response.PopulationObservation;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.utils.VariantKey;
import uk.ac.ebi.uniprot.domain.variation.Variant;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class AnnotationData {

    // Annotation options
    private boolean fun;
    private boolean pop;
    private boolean str;

    // fun (Proteins API+ProtVar scores and preds)
    // functional info is loaded from preloaded data in cache
    //FunctionalInfo funInfo FunctionalAnnService.get(String accession, int position)


    Map<String, List<Score>> scoreMap; // key: ?
    Map<String, List<Pocket>> pocketMap; // key: acc-pos
    Map<String, List<Interaction>> interactMap; // key: acc-pos
    Map<String, List<Foldx>> foldxMap;  // key: acc-pos-variantAA

    // pop
    private final Map<String, List<Variant>> variantMap;
    private final Map<String, List<AlleleFreq>> freqMap;

    // str


    public PopulationObservation get(String accession, Integer position,
                                     String chromosome, Integer genomicPosition, String altBase) {
        String variantKey = VariantKey.protein(accession, position);
        List<Variant> variants = variantMap != null
            ? variantMap.getOrDefault(variantKey, Collections.emptyList())
                : Collections.emptyList();

        String chrPosKey = VariantKey.genomic(chromosome, genomicPosition);
        List<AlleleFreq> chrPosFreqs = freqMap != null
                ? freqMap.getOrDefault(chrPosKey, Collections.emptyList())
                : Collections.emptyList();

        // Build full frequency map with copied values only
        Map<String, AlleleFreq> fullFreqMap = chrPosFreqs.stream()
                .filter(f -> f.getAlt() != null)
                .collect(Collectors.toMap(
                        f -> f.getAlt().toUpperCase(),
                        f -> f.copySubclassFields(), // copy stripped-down version
                        (f1, f2) -> f1 // in case of duplicate altBase, pick the first
                ));

        Map<String, AlleleFreq> selectedFreqMap;
        if (!StringUtils.isBlank(altBase)) {
            AlleleFreq selectedFreq = fullFreqMap.get(altBase.toUpperCase());
            selectedFreqMap = selectedFreq != null
                    ? Collections.singletonMap(altBase.toUpperCase(), selectedFreq)
                    : Collections.emptyMap();
        } else {
            selectedFreqMap = fullFreqMap;
        }

        return PopulationObservation.builder()
                .accession(accession)
                .position(position)
                .chromosome(chromosome)
                .genomicPosition(genomicPosition)
                .altBase(altBase)
                .variants(variants)
                .freqMap(selectedFreqMap)
                .build();
    }
}