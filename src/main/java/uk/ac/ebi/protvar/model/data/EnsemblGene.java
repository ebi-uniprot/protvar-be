package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Per-gene Ensembl record from the {@code rel_<rel>_ensembl_gene} dim table (Round B):
 * ensg (PK), ensgv, chromosome, reverse_strand. Loaded once into {@link uk.ac.ebi.protvar.cache.EnsemblGeneCache}.
 * Strand lives here (used in the codon/alt-allele math).
 */
@Getter
@Setter
public class EnsemblGene {
    private String ensg;
    private String ensgv;
    private String chromosome;
    private boolean reverseStrand;   // mapped automatically from reverse_strand
}
