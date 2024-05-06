package uk.ac.ebi.protvar.model.score;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AMClass {

    AMBIGUOUS(-1, "ambiguous"), BENIGN(0, "benign"), PATHOGENIC(1, "pathogenic");

    private int num;
    private String val;

    public static AMClass fromNum(int n) {
        for (AMClass value : AMClass.values()) {
            if (value.getNum() == n)
                return value;
        }
        return null;
    }
}
