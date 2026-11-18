package uk.ac.ebi.protvar.model.response;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.data.PangolinScore;

import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.ebi.protvar.processor.DownloadProcessor.pangolinValue;

class PangolinTest {

    @Test
    void significantNearSiteOver02() {
        assertTrue(Pangolin.significant(0.21f, 2));
        assertTrue(Pangolin.significant(0.21f, -2));
        assertFalse(Pangolin.significant(0.2f, 1));   // must be over 0.2
        assertFalse(Pangolin.significant(0.5f, 3));   // 3 nt away needs over 0.8
    }

    @Test
    void significantUpTo8Over08() {
        assertTrue(Pangolin.significant(0.81f, 3));
        assertTrue(Pangolin.significant(0.81f, -8));
        assertFalse(Pangolin.significant(0.8f, 5));   // must be over 0.8
        assertFalse(Pangolin.significant(0.99f, 9));  // too far
    }

    @Test
    void labels() {
        assertEquals(Pangolin.GAIN, Pangolin.label(0.3f, 1, 0f, 0));
        assertEquals(Pangolin.LOSS, Pangolin.label(0f, 0, -0.3f, -2));  // loss is stored negative
        assertEquals(Pangolin.CHANGE, Pangolin.label(0.9f, 6, -0.25f, 1));
        assertNull(Pangolin.label(0.5f, 4, -0.5f, -4));
    }

    @Test
    void ofAndCsvValue() {
        PangolinScore s = new PangolinScore();
        s.setGainScore(0.1f);
        s.setGainPos(20);
        s.setLossScore(-0.84f);
        s.setLossPos(-1);
        Pangolin p = Pangolin.of(s);
        assertEquals(Pangolin.LOSS, p.label());
        assertEquals("0.84(Splice loss predicted)", pangolinValue(p));

        s.setLossScore(-0.1f);
        assertEquals("0.1", pangolinValue(Pangolin.of(s)));
        assertNull(pangolinValue(null));
    }
}
