package uk.ac.ebi.protvar.input.parser.id;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.VariantType;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.utils.InputTypeResolver;

import static org.junit.jupiter.api.Assertions.*;

public class UniprotVarParserTest {

    @Test
    public void validIds() {
        for (String id : new String[]{"VAR_018369", "var_000012", "VAR_091618"}) {
            VariantInput input = VariantParser.parse(id);
            assertEquals(VariantFormat.UNIPROT_VAR, input.getFormat(), id);
            assertEquals(VariantType.VARIANT_ID, input.getType(), id);
            assertTrue(input.isValid(), id);
        }
    }

    @Test
    public void invalidIds() {
        for (String id : new String[]{"VAR_018369x", "VAR_1", "VAR_0183690"}) { // letters, too few / too many digits
            VariantInput input = VariantParser.parse(id);
            assertEquals(VariantFormat.UNIPROT_VAR, input.getFormat(), id);
            assertFalse(input.isValid(), id);
        }
        assertFalse(VariantParser.parse("VAR_").isValid()); // no digits: not a VAR_ ID at all
    }

    @Test
    public void notAGene() {
        assertNull(InputTypeResolver.resolveIdentifier("VAR_018369"));
    }
}
