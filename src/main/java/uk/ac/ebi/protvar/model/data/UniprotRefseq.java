package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UniprotRefseq {
    String uniprotAcc;
    String refseqAcc;
}
