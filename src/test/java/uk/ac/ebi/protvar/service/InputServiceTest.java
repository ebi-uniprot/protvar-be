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
    }

    @Test
    void normalizeInput_lineBreakIsTheOnlySeparator() {
        assertEquals(List.of("P22304 A205P", "P22304 A206G", "1 12345 A G"),
                InputService.normalizeInput("P22304 A205P\r\nP22304 A206G\n\n# comment\n1 12345 A G\n"));
        // a comma or pipe no longer separates variants: the line stays one (invalid) input
        assertEquals(List.of("P22304 A205P P22304 A206G"), InputService.normalizeInput("P22304 A205P,P22304 A206G"));
        assertEquals(List.of("P22304 A205P|P22304 A206G"), InputService.normalizeInput("P22304 A205P|P22304 A206G"));
    }

    @Test
    void normalizeInput_csvRowIsOneVariant() {
        assertEquals(List.of("1 55051215 G A"), InputService.normalizeInput("1,55051215,G,A"));
        assertEquals(List.of("7 140753336 A T"), InputService.normalizeInput("\"7\",\"140753336\",\"A\",\"T\""));
        assertEquals(List.of("P15056 V600E"), InputService.normalizeInput("P15056,V600E"));
    }

    @Test
    void normalizeInput_vcf() {
        // several ALT alleles: one variant each
        assertEquals(List.of("7 140753336 . A T", "7 140753336 . A G"),
                InputService.normalizeInput("7 140753336 . A T,G"));
        assertEquals(List.of("7\t140753336\trs1\tA\tT", "7\t140753336\trs1\tA\tG"),
                InputService.normalizeInput("7\t140753336\trs1\tA\tT,G\t50\tPASS\tAF=0.1,0.2"));
        // commas and pipes in the extra columns (INFO, VEP CSQ) are left alone: the parser ignores those columns
        String vcf = "7\t140753336\t.\tA\tT\t50\tPASS\tAF=0.1,0.2;CSQ=T|missense_variant|BRAF";
        assertEquals(List.of(vcf), InputService.normalizeInput(vcf));
    }

    @Test
    void normalizeInput_stripsWrappingQuotes() {
        assertEquals(List.of("O43822\tC61Y", "7:75933126:C:G"),
                InputService.normalizeInput("\"O43822\tC61Y\"\n'7:75933126:C:G'"));
        assertEquals(List.of("P22304 \"A205P"), InputService.normalizeInput("P22304 \"A205P"));
    }
}
