package uk.ac.ebi.protvar.input;
// todo move to uk.ac.ebi.protvar.variant?

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.parser.variantid.ClinvarParser;
import uk.ac.ebi.protvar.input.parser.variantid.CosmicParser;
import uk.ac.ebi.protvar.model.response.Message;

import java.util.*;
import java.util.stream.Collectors;

/**
 *                          VariantInput (Generic) e.g. Variant Type    VARIANT_ID
 *                            ^  ^  ^                           Format  DBSNP,CLINVAR,COSMIC
 *                           /   |  |
 *                    ______/    |  |_________
 *                  /            |            \
 *            GenomicInput  HGVSCodingInput  ProteinInput (Specialised)
 *
 *  Variant
 *  Type      GENOMIC           CODING_DNA     PROTEIN
 *  Format    VCF,GNOMAD,       HGVS_C         INTERNAL,HGVS_P
 *            INTERNAL,HGVS_G
 */
@Getter
@Setter
public class VariantInput {
	int index; // order of the input
	String inputStr; // raw input string; trimmed, non-null
	VariantFormat format;
	VariantType type; // Derived from format.type; declared here only to control json property order (doesn't need to be explicitly set)

	private final List<Message> messages = new LinkedList<>(); // to maintain insertion order

	List<GenomicVariant> derivedGenomicVariants = new ArrayList<>();

	public VariantInput(VariantFormat format, String inputStr) {
		this.format = format;
		this.inputStr = inputStr;
	}

	public VariantType getType() {
		return format.getType();
	}

	@JsonIgnore
	public List<Object[]> getChrPosList() {
		return getDerivedGenomicVariants().stream()
				.filter(g -> g.getChromosome() != null && g.getPosition() != null)
				.map(g -> new AbstractMap.SimpleEntry<>(g.getChromosome(), g.getPosition())) // removes duplicates
				.distinct()
				.map(e -> new Object[]{e.getKey(), e.getValue()})
				.collect(Collectors.toList());
	}

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
	public String getIdPrefix() {
		return switch (format) {
			case CLINVAR -> ClinvarParser.getPrefix(inputStr);
			case COSMIC -> CosmicParser.getPrefix(inputStr);
			case DBSNP -> "rs";
			default -> "";
		};
	}

	@Override
	public String toString() {
		return String.format("Input [format=%s (%s), inputStr=%s]", format,  format.getType(), inputStr);
	}

}
