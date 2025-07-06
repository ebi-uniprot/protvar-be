package uk.ac.ebi.protvar.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.format.id.ClinVarID;
import uk.ac.ebi.protvar.input.format.id.CosmicID;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.response.Message;

import java.util.*;
import java.util.stream.Collectors;

/**
 *                            UserInput
 *                            ^ ^  ^ ^
 *                           /  |  |  \___________________
 *                    ______/   |  |__________           |
 *                   |          |            |           |
 *  Type          Genomic----Coding------Protein------ID/Ref ----implements----> DerivedGenomicInputProvider
 *  (props)    chr,pos,(id),             acc,pos       id
 *             ref,alt,mappings          ref,alt_aa
 *                  |           |            |           |
 *  Format        Internal    HGVSc       Internal   DBSNP, ClinVar
 *                VCF                     HGVSc        COSMIC
 *                HGVSg
 *                GnomAD
 */
@Getter
@Setter
public abstract class UserInput {

	String inputStr;
	Type type;
	Format format;

	private final List<Message> messages = new LinkedList<>(); // to maintain insertion order

	public void addError(String text) {
		this.messages.add(new Message(Message.MessageType.ERROR, text));
	}
	public void addError(ErrorConstants error) {
		this.messages.add(new Message(Message.MessageType.ERROR, error.toString()));
	}
	public void addWarning(String text) {
		this.messages.add(new Message(Message.MessageType.WARN, text));
	}
	public void addWarning(ErrorConstants error) {
		this.messages.add(new Message(Message.MessageType.WARN, error.toString()));
	}
	public void addInfo(String text) {
		this.messages.add(new Message(Message.MessageType.INFO, text));
	}

	public boolean hasError() {
		return this.messages.stream().anyMatch(m -> m.getType() == Message.MessageType.ERROR);
	}

	@JsonIgnore
	public boolean isValid() {
		return !hasError();
	}

	@JsonIgnore
	public List<String> getErrors() {
		return messages.stream().filter(m ->  m.getType() == Message.MessageType.ERROR).map(m -> m.getText()).collect(Collectors.toList());
	}

	@JsonIgnore
	public String getClinVarIDPrefix() {
		if (this instanceof ClinVarID
				&& ((ClinVarID)this).getId() != null
				&& ((ClinVarID)this).getId().length() > ClinVarID.PREFIX_LEN) {
			return ((ClinVarID)this).getId().substring(0, ClinVarID.PREFIX_LEN);
		}
		return "";
	}

	@JsonIgnore
	public String getCosmicIDPrefix() {
		if (this instanceof CosmicID
				&& ((CosmicID)this).getId() != null
				&& ((CosmicID)this).getId().length() > CosmicID.PREFIX_LEN) {
			return ((CosmicID)this).getId().substring(0, CosmicID.PREFIX_LEN);
		}
		return "";
	}

	abstract public List<GenomicInput> getDerivedGenomicInputs();
	abstract public List<Object[]> getChrPosList(); // default impl in DerivedGenomicInputProvider
}
