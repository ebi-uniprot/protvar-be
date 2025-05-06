package uk.ac.ebi.protvar.utils;

import org.junit.jupiter.api.Test;

public class RNACodonTest {

    @Test
    void testChangeCount() {
        final int expectedSnv = 576;
        final int expectedDouble = 1728;
        final int expectedTriple = 1728;
        final int expectedNoChange = 64;

        int snvCount = 0, snvSyno = 0, snvStop = 0, snvMiss = 0;
        int doubleCount = 0, doubleSyno = 0, doubleStop = 0, doubleMiss = 0;
        int tripleCount = 0, tripleSyno = 0, tripleStop = 0, tripleMiss = 0;
        int noChangeCount = 0;

        for (CodonTable from : CodonTable.values()) {
            for (CodonTable to : CodonTable.values()) {
                CodonTable.Change change = CodonTable.fromTo(from, to);
                if (change == CodonTable.Change.SNV) {
                    snvCount += 1;
                    switch (AminoAcid.getConsequence(from.getAa(), to.getAa())) {
                        case "synonymous":
                            snvSyno += 1;
                            break;
                        case "stop gained":
                            snvStop += 1;
                            break;
                        case "missense":
                            snvMiss += 1;
                            break;
                    }
                }
                else if (change == CodonTable.Change.DOUBLE) {
                    doubleCount += 1;
                    switch (AminoAcid.getConsequence(from.getAa(), to.getAa())) {
                        case "synonymous":
                            doubleSyno += 1;
                            break;
                        case "stop gained":
                            doubleStop += 1;
                            break;
                        case "missense":
                            doubleMiss += 1;
                            break;
                    }
                }
                else if (change == CodonTable.Change.TRIPLE) {
                    tripleCount += 1;
                    switch (AminoAcid.getConsequence(from.getAa(), to.getAa())) {
                        case "synonymous":
                            tripleSyno += 1;
                            break;
                        case "stop gained":
                            tripleStop += 1;
                            break;
                        case "missense":
                            tripleMiss += 1;
                            break;
                    }
                }
                else
                    noChangeCount += 1;
            }
        }
        assert(expectedSnv == snvCount &&
                expectedDouble == doubleCount &&
                expectedTriple == tripleCount &&
                expectedNoChange == noChangeCount);
    }

}
