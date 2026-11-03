package uk.ac.ebi.protvar.input.mapper;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.ProteinInput;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.VariantType;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.model.data.Humsavar;
import uk.ac.ebi.protvar.repo.HumsavarRepo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Id2GenUniprotVarTest {

    @Test
    public void resolvesFoundAndFlagsMissing() {
        HumsavarRepo repo = mock(HumsavarRepo.class);
        when(repo.getByVarIds(any())).thenReturn(List.of(new Humsavar("VAR_004109", "P10721", 816, "D", "V")));

        ProteinInput found = (ProteinInput) VariantParser.parse("var_004109");
        ProteinInput missing = (ProteinInput) VariantParser.parse("VAR_999999");
        new Id2Gen(null, null, null, repo).uniprotVarLookup(List.of(found, missing));

        assertEquals("P10721", found.getAccession());
        assertEquals(816, found.getPosition());
        assertEquals("D", found.getRefAA());
        assertEquals("V", found.getAltAA());
        assertTrue(found.isValid());

        assertNull(missing.getAccession());
        assertFalse(missing.isValid());

    }
}
