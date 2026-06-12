package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

/**
 * A UniProt accession from the {@code rel_<rel>_uniprot_accession} reference table — the full known set
 * (canonical + isoforms) with an {@code is_canonical} flag. The canonical subset is the SwissProt-curated
 * representative; the full set is the existence reference (validity + unmapped diff). (Was UniprotEntry,
 * which was canonical-only.)
 */
@Getter
@Setter
public class UniprotAccession {
    private String accession;
    private boolean isCanonical;   // mapped via SQL alias is_canonical AS canonical
}
