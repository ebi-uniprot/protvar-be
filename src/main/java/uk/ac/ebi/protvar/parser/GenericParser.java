package uk.ac.ebi.protvar.parser;

import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class GenericParser {
    public static final String FIELD_SEPERATOR = "\\s+";
    public static final List<String> VALID_ALLELES = List.of("A", "C", "G", "T");

    public static final List<String> CHROMO_1_23 = IntStream.range(1,23).mapToObj(String::valueOf).collect(Collectors.toList());
    public static final List<String> CHROMO_X_Y = List.of("X","Y");
    public static final List<String> CHROMO_MT = Stream.of("chrM,mitochondria,mitochondrion,MT,mtDNA,mit".split(Constants.COMMA))
            .map(String::toUpperCase).collect(Collectors.toList());
    public static final String DB_MT_CHROMOSOME = "Mitochondrion";

    public static final String RS_ID_REGEX = "rs(\\d+)";

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
}
