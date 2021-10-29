package uk.ac.ebi.protvar.model.response;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.model.api.DBReference;
import uk.ac.ebi.protvar.model.api.DSPComment;
import uk.ac.ebi.protvar.model.api.DSPSequence;
import uk.ac.ebi.protvar.model.api.ProteinFeature;

@NoArgsConstructor
@Setter
@Getter
public class Protein implements Cloneable {
//	private String variant;
//	private String threeLetterCodes;
	private long position;
//	private long end;
//	private boolean canonical;
	private String accession;
	private String name;
	private String alternativeNames;
	private List<GeneName> geneNames;
	private String id;
	private String proteinExistence;
	private String type;
	private List<ProteinFeature> features = new ArrayList<>();
	// The protein API comment has an issue where text is String instead of list
	private List<DSPComment> comments;
	private DSPSequence sequence;
	private String lastUpdated;

	@JsonIgnore
	private String hgncId;
//	private String isoform;
//	private String canonicalAccession;
//	private List<String> canonicalIsoforms;
	private List<DBReference> dbReferences;

	@NoArgsConstructor
	@Setter
	@Getter
	public static class Name {
		private String full;
		private String shortName;
	}

	@Override
	public Protein clone() {
		Protein p = new Protein();
//		p.setAccession(this.getAccession());
//		p.setCanonical(this.isCanonical());
//		p.setCanonicalAccession(this.getCanonicalAccession());
		p.setHgncId(this.getHgncId());
//		p.setCanonicalIsoforms(this.getCanonicalIsoforms());
//		p.setDbReferences(this.getDbReferences());
//		p.setIsoform(this.getIsoform());
//		p.setLength(this.getLength());
		p.setName(this.getName());
		p.setType(this.getType());
		p.setFeatures(this.getFeatures());
//		p.setAssociations(this.getAssociations());
		return p;
	}

}
