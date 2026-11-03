package uk.ac.ebi.protvar.input.mapper;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.model.data.ClinVarExtended;
import uk.ac.ebi.protvar.model.data.Cosmic;
import uk.ac.ebi.protvar.repo.ClinVarRepo;
import uk.ac.ebi.protvar.repo.CosmicRepo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Lower-case and versioned IDs are looked up by their upper-case, unversioned key. */
public class Id2GenIdKeyTest {

    @Test
    public void versionedClinvarIsASingleWordId() {
        VariantInput input = VariantParser.parse("RCV000424939.1");
        assertEquals(VariantFormat.CLINVAR, input.getFormat());
        assertTrue(input.isValid());
    }

    @Test
    public void clinvarCaseAndVersion() {
        ClinVarRepo repo = mock(ClinVarRepo.class);
        ClinVarExtended cv = mock(ClinVarExtended.class);
        when(cv.getChr()).thenReturn("1"); when(cv.getPos()).thenReturn(100);
        when(cv.getRef()).thenReturn("A"); when(cv.getAlt()).thenReturn("G");
        when(repo.getByRCVMap(eq(List.of("RCV000424939")))).thenReturn(Map.of("RCV000424939", List.of(cv)));

        VariantInput lower = VariantParser.parse("rcv000424939");
        VariantInput versioned = VariantParser.parse("RCV000424939.1");
        new Id2Gen(null, repo, null, null).clinvarLookup(List.of(lower, versioned));

        assertEquals(1, lower.getDerivedGenomicVariants().size());
        assertEquals(1, versioned.getDerivedGenomicVariants().size());
        assertTrue(lower.isValid() && versioned.isValid());
    }

    @Test
    public void cosmicLowerCase() {
        CosmicRepo repo = mock(CosmicRepo.class);
        Cosmic c = Cosmic.builder().chr("1").pos(100).ref("A").alt("G").build();
        c.setId("COSV66110236");
        when(repo.getById(eq(Set.of("COSV66110236")))).thenReturn(List.of(c));
        when(repo.getByLegacyId(any())).thenReturn(List.of());

        VariantInput lower = VariantParser.parse("cosv66110236");
        new Id2Gen(null, null, repo, null).cosmicLookup(List.of(lower));

        assertEquals(1, lower.getDerivedGenomicVariants().size());
        assertTrue(lower.isValid());
    }
}
