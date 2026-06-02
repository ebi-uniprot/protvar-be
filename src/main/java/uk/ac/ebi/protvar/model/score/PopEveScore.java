package uk.ac.ebi.protvar.model.score;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PopEveScore extends Score {
    private String wt; // Now actually used
    private Double gapFreq;
    private Double popeve;
    private Double poppedEve;
    private Double poppedEsm1v;
    private Double eve;
    private Double esm1v;

    // Full constructor
    public PopEveScore(String acc, Integer pos, String wt, String mt,
                       Double gapFreq, Double popeve, Double poppedEve,
                       Double poppedEsm1v, Double eve, Double esm1v) {
        super(ScoreType.POPEVE);
        this.acc = acc;
        this.pos = pos;
        this.wt = wt;
        this.mt = mt;
        this.gapFreq = gapFreq;
        this.popeve = popeve;
        this.poppedEve = poppedEve;
        this.poppedEsm1v = poppedEsm1v;
        this.eve = eve;
        this.esm1v = esm1v;
    }

    // Minimal constructor (without acc/pos for API responses)
    public PopEveScore(String wt, String mt, Double gapFreq, Double popeve,
                       Double poppedEve, Double poppedEsm1v, Double eve, Double esm1v) {
        super(ScoreType.POPEVE);
        this.wt = wt;
        this.mt = mt;
        this.gapFreq = gapFreq;
        this.popeve = popeve;
        this.poppedEve = poppedEve;
        this.poppedEsm1v = poppedEsm1v;
        this.eve = eve;
        this.esm1v = esm1v;
    }

    @Override
    public PopEveScore copySubclassFields() {
        return new PopEveScore(null, mt, gapFreq, popeve, poppedEve,
                poppedEsm1v, eve, esm1v);
    }
}
