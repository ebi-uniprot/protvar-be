package uk.ac.ebi.protvar.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtils {

    public static final String SPACES = "\\s+";
    public static final String SPACES_OR_SLASH = "(\\s+|/)"; // spaces, or /
    public static final String SPACES_OR_SLASH_OR_GREATER = "(\\s+|/|>)";

    // regex pattern to match a word without any spaces
    public static final Pattern WORD = Pattern.compile("^\\w+$", Pattern.CASE_INSENSITIVE);

    public static final Pattern WORD_PATTERN = Pattern.compile("\\w+");

    public static boolean matchIgnoreCase(String regex, String input) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher= pattern.matcher(input);
        return matcher.matches();
    }
}
