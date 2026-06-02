package uk.ac.ebi.protvar.types;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EveClass {
    BENIGN(1),
    PATHOGENIC(2),
    UNCERTAIN(3);

    private final int value;

    public static EveClass fromValue(int value) {
        for (EveClass eveClass : EveClass.values()) {
            if (eveClass.getValue() == value)
                return eveClass;
        }
        return null;
    }
}
