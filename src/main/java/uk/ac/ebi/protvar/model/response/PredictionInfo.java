package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.model.score.*;

import java.util.List;

/**
 * Everything predicted at a protein position (GET /prediction/{accession}/{position}). Field names are the
 * prediction keys, also the values of ?include=. Residue level: conserv, pocket, interface.
 * Variant level, one entry per variant amino acid (all, or variantAA only): alphamissense, popeve, esm, eve, foldx
 * (the middle AlphaFold fragment, with numFragments), m3d.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PredictionInfo {
    private final String accession;
    private final int position;
    private final String variantAA;

    private final ConservScore conserv;
    private final List<Pocket> pocket;
    @JsonProperty("interface")
    private final List<Interaction> interfaces;   // "interface" is a Java keyword

    private final List<AmScore> alphamissense;
    private final List<PopEveScore> popeve;
    private final List<EsmScore> esm;
    private final List<EveScore> eve;
    private final List<Foldx> foldx;
    private final List<M3DPred> m3d;
}
