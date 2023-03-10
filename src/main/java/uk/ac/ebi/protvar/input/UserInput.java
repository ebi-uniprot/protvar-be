package uk.ac.ebi.protvar.input;

import java.util.*;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;

/*                   UserInput
 *                   (inputStr)
 *                   ^        ^
 *                  /type=gen  \______________.______________.
 *           GenomicInput                    |               |
 *           (id, chr,pos,ref,alt)           |               |
 * subtype   /     |          \              |type=pro       |type=rs
 *    VCFInput  HGVSInput  GnomadInput  ProteinInput       RSInput
 *                                     (acc,pos,ref,alt)  (id)
 *                                         (expandedGenInputs)
 */
@Getter
@Setter
public abstract class UserInput {
	String inputStr;

	private final List<String> invalidReasons = new ArrayList<>();

	public abstract InputType getType();
	public abstract List<GenomeProteinMapping> getMappings();

	public void addInvalidReason(String invalidReason) {
		this.invalidReasons.add(invalidReason);
	}
	public String getInvalidReasons(){
		return String.join("|", invalidReasons);
	}
	public boolean isValid(){
		return invalidReasons.isEmpty();
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
