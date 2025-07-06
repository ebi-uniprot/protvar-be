package uk.ac.ebi.protvar.input.type;

import java.util.List;
import java.util.stream.Collectors;

public interface DerivedGenomicInputProvider {
    List<GenomicInput> getDerivedGenomicInputs();

    default List<Object[]> getChrPosList() {
        return getDerivedGenomicInputs().stream()
                .filter(g -> g.getChr() != null && g.getPos() != null)
                .map(g -> new Object[]{g.getChr(), g.getPos()})
                .collect(Collectors.toList());
    }
}
