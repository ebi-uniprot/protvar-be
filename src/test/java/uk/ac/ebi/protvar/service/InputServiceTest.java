package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputServiceTest {

    @Test
    void normalizeInput_removesThousandsSeparatorsInGenomicPositions() {
        assertEquals(List.of("chr16:29847799 T>C"), InputService.normalizeInput("chr16:29,847,799 T>C"));
        assertEquals(List.of("6 95586804 A G"), InputService.normalizeInput("6 95,586,804 A G"));
        assertEquals(List.of("chr20:24971511:G>A", "1 12345 A G"),
                InputService.normalizeInput("chr20:24,971,511:G>A\n1 12345 A G"));
        // other commas still split inputs
        assertEquals(List.of("P22304 A205P", "P22304 A206G"), InputService.normalizeInput("P22304 A205P,P22304 A206G"));
        assertEquals(List.of("1", "55051215", "G", "A"), InputService.normalizeInput("1,55051215,G,A"));
    }

    @Test
    void normalizeInput_stripsWrappingQuotes() {
        assertEquals(List.of("O43822\tC61Y", "7:75933126:C:G"),
                InputService.normalizeInput("\"O43822\tC61Y\"\n'7:75933126:C:G'"));
        assertEquals(List.of("P22304 \"A205P"), InputService.normalizeInput("P22304 \"A205P"));
    }
}
