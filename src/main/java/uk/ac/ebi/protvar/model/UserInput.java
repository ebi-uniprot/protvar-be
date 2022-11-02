package uk.ac.ebi.protvar.model;

import java.util.*;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.parser.HGVSParser;
import uk.ac.ebi.protvar.parser.ProtACParser;
import uk.ac.ebi.protvar.parser.VCFParser;
import uk.ac.ebi.protvar.utils.*;

@Getter
@Setter
public class UserInput {
	public enum Type {
		VCF,
		HGVS,
		PROTAC
	}

	private static final String INPUT_END_STRING = "...";

	private Type type;
	private String chromosome;
	private Long start;
	private String id;
	private String ref;
	private String alt;
	private String inputString;

	private String accession;
	private Long proteinPosition;
	private String oneLetterRefAA;
	private String oneLetterAltAA;

	private final List<String> invalidReasons = new ArrayList<>();

	public UserInput(Type type) {
		this.type = type;
	}
	public UserInput(String accession, Long proteinPosition, String oneLetterRefAA, String oneLetterAltAA) {
		this.type = Type.PROTAC;
		this.accession = accession;
		this.proteinPosition = proteinPosition;
		this.oneLetterRefAA = oneLetterRefAA;
		this.oneLetterAltAA = oneLetterAltAA;
	}

	public static UserInput getInput(String input) {
		if (input == null)
			return null;
		if (input.startsWith("NC"))
			return HGVSParser.parse(input);
		if (ProtACParser.startsWithAccession(input))
			return ProtACParser.parse(input);
		return VCFParser.parse(input); // if (VCFParser.startsWithChromo(input))
	}

	public String getFormattedInputString() {
		if (inputString != null)
			return inputString;
		return this.chromosome + Constants.SPACE + this.start + Constants.SPACE + this.ref
				+ Constants.SLASH + this.alt + Constants.SPACE + INPUT_END_STRING;
	}

	public String getGroupBy() {
		return this.chromosome + "-" + this.start;
	}

	public void addInvalidReason(String invalidReason) {
		this.invalidReasons.add(invalidReason);
	}

	public String getInvalidReason(){
		return String.join("|", invalidReasons);
	}

	public boolean isValid(){
		return invalidReasons.isEmpty();
	}

	public static UserInput invalidObject(String userInput, Type type){
		var ret = new UserInput(type);
		ret.ref = Constants.NA;
		ret.alt = Constants.NA;
		ret.chromosome = Constants.NA;
		ret.id = Constants.NA;
		ret.start = -1L;
		ret.inputString = userInput;
		ret.invalidReasons.add(Constants.NOTE_INVALID_INPUT_CHROMOSOME);
		ret.invalidReasons.add(Constants.NOTE_INVALID_INPUT_POSITION);
		ret.invalidReasons.add(Constants.NOTE_INVALID_INPUT_REF);
		ret.invalidReasons.add(Constants.NOTE_INVALID_INPUT_ALT);
		return ret;
	}

	public static UserInput invalidProtAC(String userInput){
		var ret = new UserInput(Type.PROTAC);
		ret.inputString = userInput;
		ret.invalidReasons.add(Constants.NOTE_INVALID_INPUT_FORMAT);
		return ret;
	}

	public static UserInput invalidInput(String userInput){
		var ret = new UserInput(Type.PROTAC);
		ret.inputString = userInput;
		ret.invalidReasons.add(Constants.NOTE_INVALID_INPUT_FORMAT);
		return ret;
	}

	public static UserInput copy(UserInput input) {
		UserInput newCopy = new UserInput(input.getType());
		newCopy.setChromosome(input.getChromosome());
		newCopy.setStart(input.getStart());
		newCopy.setId(input.getId());
		newCopy.setRef(input.getRef());
		newCopy.setAlt(input.getAlt());
		newCopy.setInputString(input.getInputString());
		newCopy.setAccession(input.getAccession());
		newCopy.setProteinPosition(input.getProteinPosition());
		newCopy.setOneLetterRefAA(input.getOneLetterRefAA());
		newCopy.setOneLetterAltAA(input.getOneLetterAltAA());
		return newCopy;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		UserInput userInput = (UserInput) o;
		return Objects.equals(type, userInput.type)
				&& Objects.equals(chromosome, userInput.chromosome)
				&& Objects.equals(start, userInput.start)
				&& Objects.equals(id, userInput.id)
				&& Objects.equals(ref, userInput.ref)
				&& Objects.equals(alt, userInput.alt)
				&& Objects.equals(inputString, userInput.inputString)
				&& Objects.equals(accession, userInput.accession)
				&& Objects.equals(proteinPosition, userInput.proteinPosition)
				&& Objects.equals(oneLetterRefAA, userInput.oneLetterRefAA)
				&& Objects.equals(oneLetterAltAA, userInput.oneLetterAltAA)
				&& Objects.equals(invalidReasons, userInput.invalidReasons);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, chromosome, start, id, ref, alt, inputString, accession, proteinPosition, oneLetterRefAA, oneLetterAltAA, invalidReasons);
	}
	@Override
	public String toString() {
		return "UserInput [type=" + type + ", chromosome=" + chromosome + ", start=" + start
				+ ", id=" + id + ", ref=" + ref + ", alt=" + alt + ", inputString=" + inputString
				+ ", accession=" + accession + ", proteinPosition=" + proteinPosition
				+ ", oneLetterRefAA=" + oneLetterRefAA + ", oneLetterAltAA=" + oneLetterAltAA + "]";
	}
}
