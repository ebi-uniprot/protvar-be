package uk.ac.ebi.protvar.model.score;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EVEClass {
    BENIGN(1, "Benign"), PATHOGENIC(2, "Pathogenic"), UNCERTAIN(3, "Uncertain");

    private int num;
    private String val;

    public static EVEClass fromNum(int n) {
        for (EVEClass value : EVEClass.values()) {
            if (value.getNum() == n)
                return value;
        }
        return null;
    }
}
