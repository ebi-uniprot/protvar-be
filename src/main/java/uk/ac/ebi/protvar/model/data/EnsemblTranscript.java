package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Per-(accession × transcript) Ensembl record from the {@code rel_<rel>_ensembl_transcript} dim table
 * (Round B): ensg, enst, enstv, ensp, enspv, accession, is_mane_select; PK (accession, enst).
 * Loaded once into {@link uk.ac.ebi.protvar.cache.EnsemblTranscriptCache}, keyed by accession:enst.
 */
@Getter
@Setter
public class EnsemblTranscript {
    private String ensg;
    private String enst;
    private String enstv;
    private String ensp;
    private String enspv;
    private String accession;
    private boolean maneSelect;   // mapped via SQL alias is_mane_select AS mane_select
}
