package uk.ac.ebi.protvar.input;

import java.util.*;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.genomic.Gnomad;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.format.id.ClinVarID;
import uk.ac.ebi.protvar.input.format.id.CosmicID;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.input.format.genomic.VCF;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.model.response.Message;

/**
 *                            UserInput
 *                            ^ ^  ^ ^
 *                           /  |  |  \___________________
 *                    ______/   |  |__________           |
 *                   |          |            |           |
 *  Type          Genomic    Coding       Protein      ID/Ref
 *  (props)    chr,pos,(id),             acc,pos       id
 *             ref,alt,mappings          ref,alt_aa
 *                  |           |            |           |
 *  Format        Custom      HGVSc       Custom       DBSNP, ClinVar
 *                VCF            \        HGVSc        COSMIC
 *                HGVSg           \         |         /
 *                GnomAD           \        |        /
 *                                   derivedGenInputs
 */
@Getter
@Setter
public abstract class UserInput {

	String inputStr;
	Type type;
	Format format;

	private final List<Message> messages = new ArrayList<>();

	public void addError(String text) {
		this.messages.add(new Message(Message.MessageType.ERROR, text));
	}
	public void addWarning(String text) {
		this.messages.add(new Message(Message.MessageType.WARN, text));
	}
	public void addInfo(String text) {
		this.messages.add(new Message(Message.MessageType.INFO, text));
	}

	public boolean hasError() {
		return this.messages.stream().anyMatch(m -> m.getType() == Message.MessageType.ERROR);
	}

	public boolean isValid() {
		return !hasError();
	}

	public List<String> getErrors() {
		return messages.stream().filter(m ->  m.getType() == Message.MessageType.ERROR).map(m -> m.getText()).collect(Collectors.toList());
	}


	/**
	 * Parse input string into a UserInput object.
	 * @param inputStr	Null and empty inputs will have been filtered before calling this function.
	 *             Input string will have been trimmed as well.
	 * @return
	 */
	public static UserInput getInput(String inputStr) {
		if (inputStr == null || inputStr.isEmpty())
			return null;

		// Steps:
		// LEVEL 1 (first-level) CHECK
		// Only check if input meets "general" pattern for a specific type for e.g. if input starts with a certain
		// prefix, for e.g. XX, we assume it is of a specific type.
		// This happens at the top-level "if" condition below, before using the appropriate parser for full parsing.
		// LEVEL 2 (second-level) CHECK
		// Full parsing of input string to extract all attributes happens in L2.
		// Here we may find that we are dealing with a specific input type, but it doesn't full parse (or contain all
		// the info we require)

		// ID/ref checks - these should be single-word input
		if (DbsnpID.startsWithPrefix(inputStr)) {
			return DbsnpID.parse(inputStr);
		}
		else if (ClinVarID.startsWithPrefix(inputStr)) {
			return ClinVarID.parse(inputStr);
		}
		else if (CosmicID.startsWithPrefix(inputStr)) {
			return CosmicID.parse(inputStr);
		}
		// HGVS checks - should also be single-word input
		else if (HGVSg.maybeHGVSg(inputStr)) { //  just because check on startsWithPrefix isn't enough
			return HGVSg.parse(inputStr);
		}
		else if (HGVSc.maybeHGVSc(inputStr)) {
			return HGVSc.parse(inputStr);
		}
		else if (HGVSp.maybeHGVSp(inputStr)) {
			return HGVSp.parse(inputStr);
		}
		// GNOMAD ID check - again this is a single-word ('-' separated) input
		else if (Gnomad.isValid(inputStr)) {
			return Gnomad.parse(inputStr);
		}
		// Remaining input formats at this point
		// - custom protein -> always starts with an accession
		// - VCF and generic/custom genomic -> always starts with a chr
		else if (ProteinInput.startsWithAccession(inputStr)) {
			return ProteinInput.parse(inputStr);
		}
		else if (GenomicInput.startsWithChromo(inputStr)) {
			// VCF if input sticks to strict format, otherwise, maybe custom
			if (VCF.isValid(inputStr)) {
				return VCF.parse(inputStr);
			}
			else if (GenomicInput.isValid(inputStr)) {
				return GenomicInput.parse(inputStr);
			}
		}
		return GenomicInput.invalidInput(inputStr); // default (or most common) input is expected to be genomic, so
												// let's assume any invalid input is of GenomicInput type.
	}
}
