package uk.ac.ebi.protvar.model.score;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class M3DPred extends Score {
    private String prediction;
    private String damagingFeature;

    @Override
    public M3DPred copySubclassFields() {
        return M3DPred.builder()
                .type(ScoreType.M3D)
                .mt(null)
                .prediction(this.prediction)
                .damagingFeature(this.damagingFeature)
                .build();
    }
}
