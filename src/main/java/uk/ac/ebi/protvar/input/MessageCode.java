package uk.ac.ebi.protvar.input;

import uk.ac.ebi.protvar.model.response.Message;

import static uk.ac.ebi.protvar.model.response.Message.Type.*;

/**
 * The message catalogue: every note ProtVar attaches to an input or a result.
 *
 * <p>Each entry is named {@code PV<code>_<meaning>}; its code ({@code PV-101}) comes from the name, is
 * stable and is never reused or renumbered. Its severity is fixed here: ERROR (the line gets no result),
 * WARN (a result, read the note) or INFO. The label names the case in a few words (filters, help); the text is a format string filled from the arguments given
 * where the message is raised; it can refer to them by position ({@code %2$s}). Longer explanations live
 * on the help page, under the code.
 *
 * <p>Code groups: 1xx protein input, 2xx genomic input, 3xx HGVS, 4xx variant IDs, 5xx assembly,
 * 6xx reasons recorded by the release build (reserved), 8xx request-level, 9xx unsupported input.
 * Review sheet: dev-notebook/protvar/messages-review-2026-10-30.md.
 */
public enum MessageCode {

    // 1xx protein input
    PV101_REF_AA_MISMATCH(WARN, "Reference amino acid differs",
            "Reference differs: you gave %1$s, UniProt has %2$s at %3$d. %2$s used."),
    PV102_REF_AA_MISSING(WARN, "No reference amino acid",
            "No reference amino acid given; UniProt's (%2$s at %1$d) used."),
    PV103_ALT_AA_NOT_SNV(WARN, "Not a single-base change",
            "%2$s→%1$s needs more than one base change. Showing every change at this position."),
    PV104_ALT_AA_MISSING(WARN, "No alternative amino acid",
            "No alternative amino acid given. Showing every change at this position."),
    PV105_ALT_AA_UNKNOWN(WARN, "Alternative amino acid unknown",
            "Alternative amino acid is \"?\". Showing every change at this position."),
    PV110_ACC_NOT_MAPPED(ERROR, "No genomic mapping",
            "%s has no genomic mapping in this release."),
    PV111_ACC_NOT_HUMAN(ERROR, "Not a human accession",
            "%s is not a human UniProt accession."),
    PV112_POS_NOT_MAPPED(ERROR, "Position not mapped",
            "Position could not be mapped to the genome."),
    PV120_PROTEIN_INVALID(ERROR, "Protein input not recognised",
            "Protein input not recognised. Expected e.g. P22304 A205P."),

    // 2xx genomic input
    PV201_REF_BASE_MISMATCH(WARN, "Reference base differs",
            "Reference base differs: you gave %1$s, the genome has %2$s. %2$s used."),
    PV202_REF_ALT_BASE_MISSING(WARN, "No reference or alternative base",
            "No reference or alternative base given. Showing every change at this position."),
    PV203_ALT_BASE_MISSING(WARN, "No alternative base",
            "No alternative base given. Showing every change at this position."),
    PV204_REF_ALT_BASE_SAME(WARN, "Reference same as alternative",
            "Reference and alternative are the same. Showing every change at this position."),
    PV210_GENOMIC_INVALID(ERROR, "Genomic input not recognised",
            "Genomic input not recognised. Expected e.g. 14 89993420 A G."),

    // 3xx HGVS
    PV301_HGVS_PREFIX_NG(ERROR, "NG_ reference",
            "NG_ references aren't supported; use NC_, NM_ or NP_."),
    PV302_HGVS_PREFIX_LRG(ERROR, "LRG reference",
            "LRG references aren't supported; use NC_, NM_ or NP_."),
    PV303_HGVS_PREFIX_NR(ERROR, "NR_ reference",
            "NR_ (non-coding RNA) isn't supported; use NC_, NM_ or NP_."),
    PV304_HGVS_PREFIX_OTHER(ERROR, "Unsupported reference sequence",
            "Reference sequence type not supported; use NC_, NM_ or NP_."),
    PV305_HGVS_SCHEME_N(ERROR, "n. scheme",
            "n. (non-coding) isn't supported; use g., c. or p."),
    PV306_HGVS_SCHEME_M(ERROR, "m. scheme",
            "m. (mitochondrial) isn't supported; use g., c. or p."),
    PV307_HGVS_SCHEME_R(ERROR, "r. scheme",
            "r. (RNA) isn't supported; use g., c. or p."),
    PV308_HGVS_SCHEME_INVALID(ERROR, "HGVS scheme not recognised",
            "HGVS scheme not recognised; use g., c. or p."),
    PV309_HGVS_INVALID(ERROR, "HGVS not recognised",
            "HGVS not recognised. Check the format."),
    PV310_HGVS_NOT_SUBSTITUTION(ERROR, "Not a substitution",
            "Only substitutions are supported in HGVS."),
    PV320_G_REFSEQ_INVALID(ERROR, "g. reference invalid",
            "g. needs an NC_ accession, e.g. NC_000002.12."),
    PV321_G_REFSEQ_NOT_CHR(ERROR, "g. reference not a chromosome",
            "Reference sequence isn't a GRCh37 or GRCh38 chromosome."),
    PV322_G_CHANGE_INVALID(ERROR, "g. change not recognised",
            "g. change not recognised; expected e.g. g.123A>T."),
    PV323_G_CHR_FOR_REFSEQ(INFO, "Chromosome used for g.",
            "Chromosome used instead of NC_; position read in the selected or detected assembly."),
    PV330_C_REFSEQ_INVALID(ERROR, "c. reference invalid",
            "c. needs an NM_ accession, e.g. NM_017547.4."),
    PV331_C_CHANGE_INVALID(ERROR, "c. change not recognised",
            "c. change not recognised; expected e.g. c.1289A>G."),
    PV332_C_UTR_5(ERROR, "5′ UTR position",
            "5′ UTR positions aren't supported."),
    PV333_C_INTRONIC_5(ERROR, "Intronic position (5′)",
            "Intronic positions aren't supported."),
    PV334_C_INTRONIC_3(ERROR, "Intronic position (3′)",
            "Intronic positions aren't supported."),
    PV335_C_UTR_3(ERROR, "3′ UTR position",
            "3′ UTR positions aren't supported."),
    PV336_C_POS_MISMATCH(WARN, "c. and protein disagree",
            "The c. position and the protein change disagree."),
    PV337_C_POS_NOT_MAPPED(ERROR, "c. position not mapped",
            "c. position could not be mapped to the genome."),
    PV340_P_REFSEQ_INVALID(ERROR, "p. reference invalid",
            "p. needs an NP_ accession, e.g. NP_001305738.1."),
    PV341_P_CHANGE_INVALID(ERROR, "p. change not recognised",
            "p. change not recognised; expected e.g. p.Arg490Ser."),
    PV350_REFSEQ_VERSION_DIFFERS(INFO, "RefSeq version differs",
            "RefSeq version differs; ProtVar's version (%s) used."),
    PV351_REFSEQ_SEVERAL_ACCS(WARN, "Several accessions for RefSeq",
            "Matches several UniProt accessions (%1$s); %2$s used."),
    PV352_REFSEQ_NO_PROTEIN(ERROR, "RefSeq has no protein",
            "RefSeq ID has no UniProt protein."),
    PV353_REFSEQ_ACC_NOT_IN_UNIPROT(ERROR, "RefSeq accession not in UniProt",
            "%1$s maps to %2$s, which isn't in this UniProt release."),

    // 4xx variant IDs
    PV401_DBSNP_INVALID(ERROR, "dbSNP ID not recognised",
            "dbSNP ID not recognised; expected rs + digits."),
    PV402_DBSNP_NOT_MAPPED(ERROR, "dbSNP ID not mapped",
            "No coding mapping for this dbSNP ID."),
    PV403_CLINVAR_INVALID(ERROR, "ClinVar ID not recognised",
            "ClinVar ID not recognised; expected RCV/VCV + 9 digits."),
    PV404_CLINVAR_NOT_MAPPED(ERROR, "ClinVar ID not mapped",
            "No coding mapping for this ClinVar ID."),
    PV405_COSMIC_INVALID(ERROR, "COSMIC ID not recognised",
            "COSMIC ID not recognised; expected COSV/COSM/COSN + digits."),
    PV406_COSMIC_NOT_MAPPED(ERROR, "COSMIC ID not mapped",
            "No coding mapping for this COSMIC ID."),

    // 5xx assembly
    PV501_ASSEMBLY_GRCH38(INFO, "GRCh38 detected",
            "GRCh38 detected (%1$s%% GRCh38, %2$s%% GRCh37 matches)."),
    PV502_ASSEMBLY_GRCH37(INFO, "GRCh37 detected",
            "GRCh37 detected (%1$s%% GRCh37, %2$s%% GRCh38 matches); converted to GRCh38."),
    PV503_ASSEMBLY_UNCLEAR(WARN, "Assembly unclear",
            "Assembly unclear (%1$s%% GRCh38, %2$s%% GRCh37 matches); GRCh38 assumed."),
    PV504_GRCH37_NOT_CONVERTED(ERROR, "GRCh37 position not converted",
            "No GRCh38 position for this GRCh37 coordinate."),
    PV505_GRCH37_MOSTLY_FAILED(WARN, "GRCh37 mostly not converted",
            "Most GRCh37 positions didn't convert. If your input is GRCh38, select GRCh38 or Auto."),

    // 8xx request-level
    PV805_POS_RANGE_IGNORED(WARN, "Position range ignored",
            "Position range ignored; it applies to a single UniProt accession only."),

    // 9xx unsupported or unreadable input
    PV901_UNREADABLE_CHARS(ERROR, "Unreadable characters",
            "Unreadable characters. If you uploaded a spreadsheet, save it as plain text or CSV, one variant per line."),
    PV902_INDEL(ERROR, "Insertion or deletion",
            "Insertions and deletions aren't supported; enter a single-base change, e.g. 14 89993420 A G."),
    PV903_MULTI_BASE(ERROR, "Multi-base change",
            "Multi-base changes aren't supported; enter each base change on its own line, e.g. 14 89993420 A G."),
    PV904_PROTEIN_NO_ACC(ERROR, "Protein change without accession",
            "Protein change needs an accession, e.g. P22304 A205P."),
    PV905_GENE_SYMBOL(ERROR, "Gene symbol instead of accession",
            "Use the UniProt accession, not the gene symbol, e.g. P22304 A205P."),
    PV906_C_NO_REFSEQ(ERROR, "c. without transcript",
            "c. input needs an NM_ transcript, e.g. NM_000202.8:c.1327C>T."),
    PV907_ID_ONLY(ERROR, "ID without a change",
            "An ID alone isn't a variant; add the change, e.g. NM_000202.8:c.1327C>T."),
    PV908_ACC_ONLY(ERROR, "Accession without a change",
            "An accession alone isn't a variant; add a position or change (e.g. P22304 205), or use Browse."),
    PV909_FORMAT_UNKNOWN(ERROR, "Format not recognised",
            "Format not recognised."),
    PV910_UNREADABLE_LINE(ERROR, "Line could not be read",
            "Line could not be read."),
    PV911_EMPTY_LINE(ERROR, "Empty line",
            "Empty line.");

    private final Message.Type type;
    private final String label;
    private final String text;

    MessageCode(Message.Type type, String label, String text) {
        this.type = type;
        this.label = label;
        this.text = text;
    }

    /** The code, from the name: PV101_REF_AA_MISMATCH → PV-101. */
    public String getCode() {
        return "PV-" + name().substring(2, 5);
    }

    public Message.Type getType() {
        return type;
    }

    /** A few words naming the case, for lists and filters (the text may hold placeholders). */
    public String getLabel() {
        return label;
    }

    public String getText() {
        return text;
    }

    /** This entry as a message, its text filled from {@code args}. */
    public Message message(Object... args) {
        return new Message(type, String.format(text, args), getCode());
    }

    @Override
    public String toString() {
        return text;
    }

}
