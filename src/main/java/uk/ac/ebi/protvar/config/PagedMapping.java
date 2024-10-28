package uk.ac.ebi.protvar.config;

public class PagedMapping {

    public final static String PAGE = "1";
    public final static String PAGE_SIZE = "25";

    public final static int PAGE_SIZE_MIN = 10;
    public final static int PAGE_SIZE_MAX = 1000;
    public final static int DEFAULT_PAGE = Integer.valueOf(PAGE);
    public final static int DEFAULT_PAGE_SIZE = Integer.valueOf(PAGE_SIZE);
    public static int INPUT_EXPIRES_AFTER_DAYS = 30;
}
