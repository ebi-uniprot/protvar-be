package uk.ac.ebi.protvar.input;

public enum Format {
    VCF(Type.GENOMIC),HGVS_GEN(Type.GENOMIC), GNOMAD(Type.GENOMIC), CUSTOM_GEN(Type.GENOMIC),
    HGVS_CODING(Type.CODING),
    HGVS_PROT(Type.PROTEIN), CUSTOM_PROT(Type.PROTEIN),
    DBSNP(Type.ID), CLINVAR(Type.ID), COSMIC(Type.ID);

    Type type;
    Format(Type type) {
        this.type = type;
    }

    public static void main(String[] args) {
        System.out.println(Format.VCF);
    }
}
