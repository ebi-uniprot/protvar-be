package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.parser.ParsedField;

@Getter
@Setter
public class HGVSCodingInput extends UserInput {

    // derived fields
    String derivedUniprotAcc;
    Integer derivedProtPos;
    Integer derivedCodonPos;

    public HGVSCodingInput(String inputStr) {
        super(Format.HGVS_CODING, inputStr);
    }

    // parsed fields setters and getters
    // setter/getter for rsAcc in UserInput
    public void setPos(Integer pos) { // Coding DNA position
        getParsedFields().put(ParsedField.POS, pos);
    }

    public Integer getPos() {
        return (Integer) getParsedFields().get(ParsedField.POS);
    }
    public void setRef(String ref) {
        getParsedFields().put(ParsedField.REF, ref);
    }
    public String getRef() {
        return (String) getParsedFields().get(ParsedField.REF);
    }
    public void setAlt(String alt) {
        getParsedFields().put(ParsedField.ALT, alt);
    }
    public String getAlt() {
        return (String) getParsedFields().get(ParsedField.ALT);
    }

    // Optional parsed fields
    public void setGene(String gene) {
        getParsedFields().put(ParsedField.GENE, gene);
    }
    public String getGene() {
        return (String) getParsedFields().get(ParsedField.GENE);
    }
    public void setProtRef(String protRef) {
        getParsedFields().put(ParsedField.PROT_REF, protRef);
    }
    public String getProtRef() {
        return (String) getParsedFields().get(ParsedField.PROT_REF);
    }
    public void setProtAlt(String protAlt) {
        getParsedFields().put(ParsedField.PROT_ALT, protAlt);
    }
    public String getProtAlt() {
        return (String) getParsedFields().get(ParsedField.PROT_ALT);
    }
    public void setProtPos(Integer protPos) {
        getParsedFields().put(ParsedField.PROT_POS, protPos);
    }
    public Integer getProtPos() {
        return (Integer) getParsedFields().get(ParsedField.PROT_POS);
    }
}
