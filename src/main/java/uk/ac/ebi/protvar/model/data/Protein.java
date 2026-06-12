package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Per-accession protein metadata from the {@code rel_<rel>_protein} dim table (Round B):
 * accession (PK), protein_name, gene_name, is_canonical, length. Loaded once into {@link uk.ac.ebi.protvar.cache.ProteinCache}.
 */
@Getter
@Setter
public class Protein {
    private String accession;
    private String proteinName;
    private String geneName;
    private boolean isCanonical;   // mapped via SQL alias is_canonical AS canonical
    private Integer length;
}
