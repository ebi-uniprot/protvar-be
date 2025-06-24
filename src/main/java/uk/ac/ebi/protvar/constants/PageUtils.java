package uk.ac.ebi.protvar.constants;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;

public class PageUtils {

    public final static String PAGE = "1";
    public final static String PAGE_SIZE = "25";

    public final static int PAGE_SIZE_MIN = 10;
    public final static int PAGE_SIZE_MAX = 1000;
    public final static int DEFAULT_PAGE = Integer.parseInt(PAGE);
    public final static int DEFAULT_PAGE_SIZE = Integer.parseInt(PAGE_SIZE);

    /*
    * Returns a sublist of the full list based on the page number and page size.
    * The page number is 1-based, meaning that page 1 corresponds to the first set of items.
    */
    public static List getPage(List fullList, int page, int pageSize) {
        if(pageSize <= 0 || page <= 0) {
            return Collections.emptyList();
        }
        int fromIndex = (page - 1) * pageSize;
        if(fullList == null || fullList.size() <= fromIndex) {
            return Collections.emptyList();
        }
        // toIndex exclusive
        return fullList.subList(fromIndex, Math.min(fromIndex + pageSize, fullList.size()));
    }

    public static <T> Page<T> emptyPage(Pageable pageable) {
        return pageable == null ? Page.empty() : Page.empty(pageable);
    }
}
