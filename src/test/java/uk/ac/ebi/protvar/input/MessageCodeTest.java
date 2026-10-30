package uk.ac.ebi.protvar.input;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.protvar.model.response.Message;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCodeTest {

    private static final Pattern SPEC = Pattern.compile("%(?:(\\d+)\\$)?[,]?([a-zA-Z%])");

    // dummy arguments for a text's placeholders, by position (%2$s) or in order (%s): a number for %d
    private static Object[] args(String text) {
        TreeMap<Integer, Object> args = new TreeMap<>();
        int next = 1;
        Matcher m = SPEC.matcher(text);
        while (m.find()) {
            if ("%".equals(m.group(2))) continue;
            int pos = m.group(1) != null ? Integer.parseInt(m.group(1)) : next++;
            args.put(pos, "d".equals(m.group(2)) ? (Object) 1 : "x");
        }
        return args.values().toArray();
    }

    // every text formats with its own placeholders (a bad one, e.g. %t, throws at runtime → HTTP 500);
    // names are PV<code>_<meaning>, codes are unique, and unsupported input (9xx) is always an ERROR
    @Test
    void everyEntryIsWellFormed() {
        Set<String> codes = new HashSet<>();
        for (MessageCode c : MessageCode.values()) {
            assertTrue(c.name().matches("PV\\d{3}_[A-Z0-9_]+"), c.name());
            assertTrue(codes.add(c.getCode()), "duplicate " + c.getCode());
            assertTrue(!c.getCode().startsWith("PV-9") || c.getType() == Message.Type.ERROR, c.name() + " should be ERROR");
            assertDoesNotThrow(() -> c.message(args(c.getText())), c.name());
        }
    }

    @Test
    void codeComesFromTheName() {
        Message m = MessageCode.PV101_REF_AA_MISMATCH.message("Gln", "Arg", 248, "Arg");
        assertEquals("PV-101", m.getCode());
        assertEquals(Message.Type.WARN, m.getType());
        assertEquals("Reference differs: you gave Gln, UniProt has Arg at 248. Arg used.", m.getText());
    }
}
