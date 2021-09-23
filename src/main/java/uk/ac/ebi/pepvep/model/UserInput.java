package uk.ac.ebi.pepvep.model;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.Getter;
import uk.ac.ebi.pepvep.exception.InvalidInputException;
import uk.ac.ebi.pepvep.utils.Chromosome2RefSeqId;
import uk.ac.ebi.pepvep.utils.Commons;
import uk.ac.ebi.pepvep.utils.Constants;
import uk.ac.ebi.pepvep.utils.ExtractUtils;

@Getter
public class UserInput {

	private static final String INPUT_END_STRING = "...";
	private static final String FIELD_SEPERATOR = "\\s+";
	private static final List<String> validAllele = List.of("A", "C", "G", "T");
	private static final List<String> chromosomes1to22 = IntStream.range(1,23).mapToObj(String::valueOf).collect(Collectors.toList());
	private static final List<String> chromosomesXAndY = List.of("X","Y");
	private static final List<String> chromosomeMitochondria = Stream.of("chrM,mitochondria,mitochondrion,MT,mtDNA,mit".split(Constants.COMMA))
		.map(String::toUpperCase).collect(Collectors.toList());

	static final String DB_MT_CHROMOSOME = "Mitochondrion";

	private String chromosome;
	private Long start;
	private String id;
	private String ref;
	private String alt;
	private String inputString;
	private final List<String> invalidReasons = new ArrayList<>();

	@Override
	public String toString() {
		return "UserInput [chromosome=" + chromosome + ", start=" + start + ", ref=" + ref + ", inputString="
				+ inputString + "]";
	}

	public static UserInput getInput(String input) {
		if (input == null)
			return null;
		if (input.startsWith("NC"))
			return HGVSParser.parse(input);
		return VCFParser.parse(input);
	}

	static String convertChromosome(String chromosome) {
		chromosome = Commons.trim(chromosome).toUpperCase();
		if (chromosomes1to22.contains(chromosome) || chromosomesXAndY.contains(chromosome))
			return chromosome;
		if (chromosomeMitochondria.contains(chromosome))
			return DB_MT_CHROMOSOME;
		return Constants.NA;
	}

	static Long convertPosition(String sPosition) {
		long position = -1L;
		try {
			position = Long.parseLong(sPosition.trim());
		} catch (NumberFormatException | NullPointerException ignored) {
		}
		if (position <= 0)
			position = -1L;
		return position;
	}

	static boolean isReferenceAndAlternativeAllele(String element){
		element = Commons.trim(element);
		return element.length() == 3 && element.contains(Constants.SLASH) && isAllele(element.split(Constants.SLASH)[0]) && isAllele(element.split(Constants.SLASH)[1]);
	}

	static boolean isAllele(String element){
		return validAllele.contains(Commons.trim(element).toUpperCase());
	}

	public String getInputString() {
		if (inputString != null)
			return inputString;
		return this.chromosome + Constants.SPACE + this.start + Constants.SPACE + this.ref
				+ Constants.SLASH + this.alt + Constants.SPACE + INPUT_END_STRING;
	}

	private void setRef(String allele) throws InvalidInputException {
		if (!validAllele.contains(allele)) {
			throw new InvalidInputException("Invalid input : location");
		}
		ref = allele;

	}

	private void setAlt(String alternate) throws InvalidInputException {
		if (!validAllele.contains(alternate)) {
			throw new InvalidInputException("Invalid input : location");
		}
		alt = alternate;

	}

	private void setStart(Long startLoc) {
		start = startLoc;

	}

	private void setChromosome(String chr) {
		chromosome = chr;
	}

	public String getGroupBy() {
		return this.chromosome + "-" + this.start;
	}

	public boolean isValid(){
		return invalidReasons.isEmpty();
	}

	public String getInvalidReason(){
		return String.join("|", invalidReasons);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		UserInput userInput = (UserInput) o;
		return Objects.equals(chromosome, userInput.chromosome) && Objects.equals(start, userInput.start) && Objects.equals(id, userInput.id) && Objects.equals(ref, userInput.ref) && Objects.equals(alt, userInput.alt) && Objects.equals(inputString, userInput.inputString) && Objects.equals(invalidReasons, userInput.invalidReasons);
	}

	@Override
	public int hashCode() {
		return Objects.hash(chromosome, start, id, ref, alt, inputString, invalidReasons);
	}

	static UserInput invalidObject(String userInput){
		var ret = new UserInput();
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

	static class VCFParser {
		public static UserInput parse(String input) {
			if (input == null || input.isBlank())
				return invalidObject(input);

			input = input.trim();
			UserInput userInput = new UserInput();
			userInput.inputString = input;
			LinkedList<String> tokens = new LinkedList<>(Arrays.asList(input.split(FIELD_SEPERATOR)));

			var userChromosome = Commons.trim(tokens.poll());
			String chromosome = convertChromosome(userChromosome);
			if (chromosome.equals(Constants.NA))
				userInput.invalidReasons.add(Constants.NOTE_INVALID_INPUT_CHROMOSOME + userChromosome);
			userInput.chromosome = chromosome;

			var userPosition = Commons.trim(tokens.poll());
			long position = convertPosition(userPosition);
			if (position <= 0)
				userInput.invalidReasons.add(Constants.NOTE_INVALID_INPUT_POSITION + userPosition);
			userInput.start = position;

			if (isIdExist(tokens)) {
				var tokenId = Commons.trim(tokens.poll());
				userInput.id = tokenId.isEmpty() ? Constants.NA : tokenId;
			}else {
				userInput.id = Constants.NA;
			}

			var token = Commons.trim(tokens.poll());
			if (isReferenceAndAlternativeAllele(token)) {
				userInput.ref = token.split(Constants.SLASH)[0].toUpperCase();
				userInput.alt = token.split(Constants.SLASH)[1].toUpperCase();
			} else {
				if (isAllele(token))
					userInput.ref = token.toUpperCase();
				else {
					userInput.ref = Constants.NA;
					userInput.invalidReasons.add(Constants.NOTE_INVALID_INPUT_REF + token);
				}
				token = Commons.trim(tokens.poll());
				if (isAllele(token))
					userInput.alt = token.toUpperCase();
				else {
					userInput.alt = Constants.NA;
					userInput.invalidReasons.add(Constants.NOTE_INVALID_INPUT_ALT + token);
				}
			}
			return userInput;
		}

		static boolean isIdExist(LinkedList<String> remainingTokens) {
			if(remainingTokens.isEmpty())
				return false;
			if(isReferenceAndAlternativeAllele(remainingTokens.getFirst()))
				return false;
			return !isAllele(remainingTokens.getFirst());
		}
	}

	static class HGVSParser{
		public static UserInput parse(String hgvs) {
			try {
				String refSeq = hgvs.split(":g")[0];
				String chromosome = Chromosome2RefSeqId.getChromosome(refSeq);
				Long startLoc = ExtractUtils.extractLocation(hgvs);
				String allele = ExtractUtils.extractAllele(hgvs, null);
				String[] alleles = allele.split(Constants.VARIANT_SEPARATOR);

				UserInput input = new UserInput();
				input.setChromosome(chromosome);
				input.setStart(startLoc);
				input.setRef(alleles[0]);
				input.setAlt(alleles[1]);
				return input;
			} catch (Exception ex) {
				return invalidObject(hgvs);
			}
		}
	}
}
