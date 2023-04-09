package uk.ac.ebi.protvar.input;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.model.response.Message;

/*                   UserInput
 *                   -inputStr
 *                   ^        ^
 *                  /type=gen  \______________.______________.
 *           GenomicInput                    |               |
 *           -chr,pos,id,ref,alt             |               |
 *           -mappings,converted             |               |
 * subtype   /     |          \              |type=pro       |type=rs
 *    VCFInput  HGVSInput  GnomadInput  ProteinInput       RSInput
 *                                     -acc,pos,ref,alt    -id
 *                                     - derivedGenInputs  -derivedGenInputs
 */
@Getter
@Setter
public abstract class UserInput {
	String inputStr;

	private final List<Message> messages = new ArrayList<>();

	public abstract InputType getType();

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

	public static UserInput getInput(String input) {
		if (input == null)
			return null;
		if (Pattern.matches(RSInput.RS_ID_REGEX, input))
			return new RSInput(input);
		if (Pattern.matches(GnomadInput.GNOMAD_ID_REGEX, input))
			return new GnomadInput(input);
		if (input.startsWith(HGVSInput.HGVS_PREFIX))
			return new HGVSInput(input);
		if (ProteinInput.startsWithAccession(input))
			return new ProteinInput(input);
		if (VCFInput.startsWithChromo(input))
			return new VCFInput(input);
		return GenomicInput.invalidGenomicInput(input); // default (or most common) input is expected to be genomic, so
														// let's assume any invalid input is of GenomicInput type.
	}
}
