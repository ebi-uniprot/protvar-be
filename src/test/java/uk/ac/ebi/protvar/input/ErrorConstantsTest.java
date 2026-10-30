package uk.ac.ebi.protvar.input;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ErrorConstantsTest {

    private static final Pattern SPEC = Pattern.compile("%(%|[,]?([a-zA-Z]))");

    // every message must format with its own placeholders (a bad one, e.g. %t, throws at runtime → HTTP 500)
    @Test
    void everyMessageFormats() {
        for (ErrorConstants e : ErrorConstants.values()) {
            List<Object> args = new ArrayList<>();
            Matcher m = SPEC.matcher(e.getErrorMessage());
            while (m.find()) {
                if (m.group(2) != null) args.add("d".equals(m.group(2)) ? (Object) 1 : "x");
            }
            assertDoesNotThrow(() -> String.format(e.getErrorMessage(), args.toArray()), e.name());
        }
    }
}
