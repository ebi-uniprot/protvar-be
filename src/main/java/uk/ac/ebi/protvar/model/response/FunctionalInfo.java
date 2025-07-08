package uk.ac.ebi.protvar.model.response;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.model.score.ConservScore;
import uk.ac.ebi.protvar.model.score.EsmScore;
import uk.ac.ebi.protvar.model.score.EveScore;
import uk.ac.ebi.uniprot.domain.entry.DbReference;
import uk.ac.ebi.uniprot.domain.entry.Gene;
import uk.ac.ebi.uniprot.domain.entry.Sequence;
import uk.ac.ebi.uniprot.domain.entry.comment.Comment;
import uk.ac.ebi.uniprot.domain.features.Feature;
import uk.ac.ebi.protvar.model.data.Foldx;
import uk.ac.ebi.protvar.model.data.Interaction;
import uk.ac.ebi.protvar.model.data.Pocket;

@NoArgsConstructor
@Setter
@Getter
public class FunctionalInfo {
	// Common UPEntry fields
	private String accession;
	private String entryId;
	private String proteinExistence;
	private List<Gene> gene;
	private List<Comment> comments;
	private List<Feature> features; // filtered - isWithinLocationRange
	private List<DbReference> dbReferences; // filtered - refType in "InterPro", "Pfam", "CATH"
	private Sequence sequence;

	// ProteinsAPI2FunctionalInfo processed fields
	private int position;
	private String type;
	private String name;
	private String alternativeNames; // built from ...
	private String lastUpdated;

	// ProtVar predictions
	private List<Pocket> pockets;
	private List<Foldx> foldxs;
	private List<Interaction> interactions;

	// ProtVar scores (CADD and AM scores in core mapping)
	private ConservScore conservScore;
	private EveScore eveScore;
	private EsmScore esmScore;

}
