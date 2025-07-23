package uk.ac.ebi.protvar.model.response;

import java.util.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.VariantInput;

/**
 *  GenomicInput
 *  └── List<Gene>
 *      └── List<Isoform>
 *          └── List<Transcript>
 *              ├── String enst
 *              ├── String ensp
 *              └── String ense
 *
 * Gene (ENSG) → has multiple ENSTs (transcripts), representing different isoforms of the gene due to splicing.
 *
 * Transcript (ENST) → gets translated into a Protein (ENSP). Multiple ENSPs can arise from the same ENST if there are different possible protein isoforms from the same transcript.
 *
 *   +--------------------+
 *   |   Gene (ENSG)      |
 *   +--------------------+
 *         |
 *         |  (splicing)
 *         v
 *   +---------------------+
 *   | Transcript (ENST)   |     → This is the mRNA form of the gene
 *   +---------------------+
 *         |
 *         |  (translation)
 *         v
 *   +---------------------+
 *   | Protein (ENSP)      |     → This is the protein produced from the transcript
 *   +---------------------+
 *         |
 *         |  (may result in multiple isoforms)
 *         v
 *   +---------------------+
 *   | Peptide (ENSE)      |     → This is a specific peptide/protein sequence form of ENSP
 *   +---------------------+
 *
 * ENSG (Gene)
 *  ├── ENST (Transcript)
 *  │    ├── ENSP (Protein)
 *  │    │    └── ENSE (Peptide)
 *  │    ├── ENSP (Protein)
 *  │    └── ENSP (Protein)
 *  └── ENST (Transcript)
 *       ├── ENSP (Protein)
 *       └── ENSP (Protein)
 *
 * Ensembl's ID hierarchy (simplified):
 * ENSG (gene)
 * └── ENST (transcript)
 *     └── ENSP (protein product of transcript)
 *         └── ENSE (exon, often inferred or referenced)
 */
@Setter
@Getter
public class MappingResponse {

	// todo: add request field
	List<VariantInput> inputs;  // todo: rename to mappings? Mapping object with each input line, and corresponding
	// mapped list of genes Mapping(input, [genes])
	// perhaps a Map of input(variant) to List<Gene>?
	List<Message> messages;

	public MappingResponse(List<VariantInput> inputs) {
		this.inputs = inputs;
		this.messages = new ArrayList<>();
	}
}
