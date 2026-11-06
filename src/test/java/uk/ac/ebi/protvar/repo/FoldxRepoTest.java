package uk.ac.ebi.protvar.repo;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.data.Foldx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FoldxRepoTest {

    private static Foldx fx(String afId, double ddg) {
        return new Foldx("Q8WZ42", 20001, afId, 1, "I", "V", ddg, 90.0, 1);
    }

    @Test
    void middleFragmentByNumberNotText() throws Exception {
        // titin 20001: F95..F101; as text F100 < F95, so the middle was F96; by number it is F98
        List<Foldx> rows = new ArrayList<>(List.of(fx("F100", 1), fx("F101", 2), fx("F95", 3), fx("F96", 4),
                fx("F97", 5), fx("F98", 6), fx("F99", 7)));
        var m = FoldxRepo.class.getDeclaredMethod("groupAndReturnMiddleFragment", List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Foldx> out = (List<Foldx>) m.invoke(new FoldxRepo(null), rows);
        assertEquals(1, out.size());
        assertEquals("F98", out.get(0).getAfId());
        assertEquals(7, out.get(0).getNumFragments());
    }
}
