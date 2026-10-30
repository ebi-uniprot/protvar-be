package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.MessageCode;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadFilterNoteCodesTest {

    private static VariantInput line(int idx, String text, MessageCode... codes) {
        VariantInput input = VariantParser.parse(text);
        input.setOriginalIndex(idx);
        for (MessageCode c : codes) input.addMessage(c, "x", "y", 1, "z");
        return input;
    }

    @Test
    void distinctCodesPerLineOnlyForLinesWithNotes() {
        Map<Integer, List<String>> codes = UploadFilterService.noteCodes(List.of(
                line(0, "P04637 R175H"),
                line(1, "P04637 R175F", MessageCode.PV103_ALT_AA_NOT_SNV, MessageCode.PV103_ALT_AA_NOT_SNV),
                line(2, "BRCA1 C61G")));   // unsupported: the parser adds PV-905
        assertEquals(Map.of(1, List.of("PV-103"), 2, List.of("PV-905")), codes);
    }
}
