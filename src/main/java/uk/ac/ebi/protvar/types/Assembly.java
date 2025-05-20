package uk.ac.ebi.protvar.types;

import java.util.HashMap;
import java.util.Map;

public enum Assembly {

    GRCH38("GRCh38", "h38", "38"),
    GRCH37("GRCh37", "h37", "37");

    public static final Assembly DEFAULT_ASSEMBLY = Assembly.GRCH38;

    // properties with different names (or version num) each assembly is known
    public final String name;
    public final String shortName;
    public final String version;

    private static final Map<String, Assembly> BY_NAME_STR = new HashMap<>();

    static {
        for (Assembly e: values()) {
            BY_NAME_STR.put(e.name.toUpperCase(), e);
            BY_NAME_STR.put(e.shortName.toUpperCase(), e);
            BY_NAME_STR.put(e.version.toUpperCase(), e);
        }
    }

    private Assembly(String name, String shortName, String version) {
        this.name = name;
        this.shortName = shortName;
        this.version = version;
    }

    public static Assembly of(String str) {
        if (str == null)
            return null;
        return BY_NAME_STR.get(str.toUpperCase());
    }

    public static boolean autodetect(String assembly) {
        return assembly != null && assembly.equalsIgnoreCase("AUTO");
    }

    public static boolean is37(String assembly) {
        Assembly parsedAssembly = Assembly.of(assembly);
        return parsedAssembly != null && parsedAssembly == Assembly.GRCH37;
    }

}