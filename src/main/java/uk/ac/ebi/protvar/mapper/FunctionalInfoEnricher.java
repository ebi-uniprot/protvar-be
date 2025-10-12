package uk.ac.ebi.protvar.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.model.score.*;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FunctionalInfoEnricher {

    public void enrich(FunctionalInfo functionalInfo, AnnotationData annData, String variantAA) {
        if (functionalInfo == null) return;

        String accession = functionalInfo.getAccession();
        int position = functionalInfo.getPosition();

        // Add novel predictions
        String variantKey = VariantKey.protein(accession, position);
        String variantKeyWithAlt = VariantKey.protein(accession, position, variantAA);
        if (annData.getPocketMap() != null || !annData.getPocketMap().isEmpty()) {
            List<Pocket> pockets = annData.getPocketMap().getOrDefault(variantKey, Collections.emptyList());
            functionalInfo.setPockets(pockets);
        }

        if (annData.getInteractMap() != null || !annData.getInteractMap().isEmpty()) {
            List<Interaction> interactions = annData.getInteractMap().get(variantKey);
            functionalInfo.setInteractions(interactions);
        }

        if (annData.getFoldxMap() != null || !annData.getFoldxMap().isEmpty()) {
            List<Foldx> foldxs = annData.getFoldxMap().get(variantKeyWithAlt);
            functionalInfo.setFoldxs(foldxs);
        }

        // Add annotation (Conserv, Eve, Esm, PopEve) scores
        if (annData.getScoreMap() != null || !annData.getScoreMap().isEmpty()) {

            String conservScoreKey = VariantKey.protein(ScoreType.CONSERV, accession, position, null);
            String eveScoreKey = VariantKey.protein(ScoreType.EVE, accession, position, variantAA);
            String esmScoreKey = VariantKey.protein(ScoreType.ESM, accession, position, variantAA);
            String popeveScoreKey = VariantKey.protein(ScoreType.POPEVE, accession, position, variantAA);

            annData.getScoreMap().getOrDefault(conservScoreKey, Collections.emptyList()).stream().findFirst()
                    .map(s -> ((ConservScore) s).copySubclassFields()).ifPresent(functionalInfo::setConservScore);

            annData.getScoreMap().getOrDefault(eveScoreKey, Collections.emptyList()).stream().findFirst()
                    .map(s -> ((EveScore) s).copySubclassFields()).ifPresent(functionalInfo::setEveScore);

            annData.getScoreMap().getOrDefault(esmScoreKey, Collections.emptyList()).stream().findFirst()
                    .map(s -> ((EsmScore) s).copySubclassFields()).ifPresent(functionalInfo::setEsmScore);

            annData.getScoreMap().getOrDefault(popeveScoreKey, Collections.emptyList()).stream().findFirst()
                    .map(s -> ((PopEveScore) s).copySubclassFields()).ifPresent(functionalInfo::setPopEveScore);
        }
    }

}
