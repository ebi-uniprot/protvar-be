package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Getter
@Setter
public class GenomicInput extends UserInput {
    public static final List<String> VALID_ALLELES = List.of("A", "C", "G", "T");
    public static final List<String> CHROMO_1_23 = IntStream.range(1,23).mapToObj(String::valueOf).collect(Collectors.toList());
    public static final List<String> CHROMO_X_Y = List.of("X","Y");
    public static final List<String> CHROMO_MT = Stream.of("chrM,mitochondria,mitochondrion,MT,mtDNA,mit".split(Constants.COMMA))
            .map(String::toUpperCase).collect(Collectors.toList());
    public static final String DB_MT_CHROMOSOME = "Mitochondrion";

    String chr;
    Long pos;
    String ref;
    String alt;

    String id;

    Boolean converted;

    // output or result of input
    List<GenomeProteinMapping> mappings = new ArrayList<>();

    @Override
    public InputType getType() {
        return InputType.GEN;
    }

    public String groupByChrAndPos() {
        return this.chr + "-" + this.pos;
    }

    public static UserInput invalidInput(String userInput){
        GenomicInput invalid = new GenomicInput();
        invalid.inputStr = userInput;
        invalid.addError("Error parsing user input");
        return invalid;
    }

    public static Long convertPosition(String sPosition) {
        long position = -1L;
        try {
            position = Long.parseLong(sPosition.trim());
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        if (position <= 0)
            position = -1L;
        return position;
    }

    public static String convertChromosome(String chromosome) {
        chromosome = Commons.trim(chromosome).toUpperCase();
        if (CHROMO_1_23.contains(chromosome) || CHROMO_X_Y.contains(chromosome))
            return chromosome;
        if (CHROMO_MT.contains(chromosome))
            return DB_MT_CHROMOSOME;
        return Constants.NA;
    }

    public static boolean isAllele(String element){
        return VALID_ALLELES.contains(Commons.trim(element).toUpperCase());
    }

    @Override
    public String toString() {
        return "GenomicInput [chr=" + chr + ", pos=" + pos + ", ref=" + ref
                + ", alt=" + alt + "]";
    }

    public String getGroupBy() {
        return this.chr + "-" + this.pos;
    }

    public List<GenomeProteinMapping> getMappings() {
        return this.mappings;
    }


    public static boolean startsWithChromo(String input, String sep) {
        String[] params = input.split(sep);
        if (params.length > 0){
            String p1 = params[0].toUpperCase();
            return CHROMO_1_23.contains(p1) || CHROMO_X_Y.contains(p1) || CHROMO_MT.contains(p1);
        }
        return false;
    }
}
