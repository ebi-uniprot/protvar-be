package uk.ac.ebi.protvar.input;

public enum Format {
    VCF(Type.GENOMIC),HGVS_GEN(Type.GENOMIC), GNOMAD(Type.GENOMIC), INTERNAL_GENOMIC(Type.GENOMIC),
    HGVS_CODING(Type.CODING),
    HGVS_PROT(Type.PROTEIN), INTERNAL_PROTEIN(Type.PROTEIN),
    DBSNP(Type.ID), CLINVAR(Type.ID), COSMIC(Type.ID);

    Type type;
    Format(Type type) {
        this.type = type;
    }

}
