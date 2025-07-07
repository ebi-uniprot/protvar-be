package uk.ac.ebi.protvar.input.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputParser {
    protected static final Logger LOGGER = LoggerFactory.getLogger(InputParser.class);

    public static final String BASE = "(A|T|C|G)";
    public static final String POS = "([0-9]*[1-9][0-9]*)";  // positive-only integers incl. w/ leading zeros

}
