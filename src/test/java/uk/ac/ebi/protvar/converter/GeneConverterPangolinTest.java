package uk.ac.ebi.protvar.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.data.PangolinScore;
import uk.ac.ebi.protvar.model.response.Pangolin;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeneConverterPangolinTest {

    private static PangolinScore row(String alt, String gene, double gain, double loss) {
        PangolinScore s = new PangolinScore();
        s.setAltAllele(alt); s.setGeneId(gene); s.setGainScore(gain); s.setLossScore(loss);
        s.setGainPos(-50); s.setLossPos(37);
        return s;
    }

    @Test
    public void matchesVersionedEnsgAndAlt() {
        List<PangolinScore> rows = List.of(
                row("C", "ENSG00000157404", 0, 0),
                row("T", "ENSG00000157404", 0, -0.01),
                row("T", "ENSG00000999999", 0.4, 0)); // overlapping gene: its own score
        Pangolin p = new GeneConverter(null).findPangolin(rows, "T", "ENSG00000157404.18");
        assertNotNull(p);
        assertEquals(0.01, p.score(), 1e-9);
        assertEquals(-0.01, p.loss(), 1e-9);
        assertEquals(37, p.lossPos());
        assertNull(new GeneConverter(null).findPangolin(rows, "G", "ENSG00000157404.18"));
        assertNull(new GeneConverter(null).findPangolin(rows, "T", "ENSG00000000001.1"));
    }
}
