package uk.ac.ebi.protvar.processor;

import uk.ac.ebi.protvar.utils.Constants;

public final class CsvHeaders {

    private CsvHeaders() {
        // Utility class; prevent instantiation
    }

    public static final String INPUT = "User_input,Chromosome,Coordinate,ID,Reference_allele,Alternative_allele";
    public static final String NOTES = "Notes";

    public static final String OUTPUT_MAPPING = String.join(",",
            "Gene",
            "Codon_change",
            "Strand",
            "CADD_phred_like_score",
            "Canonical_isoform_transcripts",
            "MANE_transcript",
            "Uniprot_canonical_isoform_(non_canonical)",
            "Alternative_isoform_mappings",
            "Protein_name",
            "Amino_acid_position",
            "Amino_acid_change",
            "Consequences"
    ); // length = 12

    public static final String OUTPUT_FUNCTION = String.join(",",
            "Residue_function_(evidence)",
            "Region_function_(evidence)",
            "Protein_existence_evidence",
            "Protein_length",
            "Entry_last_updated",
            "Sequence_last_updated",
            "Protein_catalytic_activity",
            "Protein_complex",
            "Protein_sub_cellular_location",
            "Protein_family",
            "Protein_interactions_PROTEIN(gene)",
            "Predicted_pockets(energy;per_vol;score;resids)",
            "Predicted_interactions(chainA-chainB;a_resids;b_resids;pDockQ)",
            "Foldx_prediction(foldxDdg;plddt)",
            "Conservation_score",
            "AlphaMissense_pathogenicity(class)",
            "EVE_score(class)",
            "ESM1b_score"
    ); // length = 18

    public static final String OUTPUT_POPULATION = String.join(",",
            "Gnomad_allele_freq(ac;an;af)",
            "Genomic_location",
            "Cytogenetic_band",
            "Other_identifiers_for_the_variant",
            "Diseases_associated_with_variant",
            "Variants_colocated_at_residue_position"
    ); // length = 6

    public static final String OUTPUT_STRUCTURE = "Position_in_structures"; // length = 1

    public static final String OUTPUT = String.join(",",
            OUTPUT_MAPPING,
            OUTPUT_FUNCTION,
            OUTPUT_POPULATION,
            OUTPUT_STRUCTURE
    ); // total = 37

    public static final int OUTPUT_LENGTH = OUTPUT.split(Constants.COMMA).length; // total = 36

    public static final String CSV_HEADER = String.join(",", INPUT, NOTES, OUTPUT);
}
