package uk.ac.ebi.protvar.utils;


import uk.ac.ebi.protvar.model.MappingRequest;

import java.util.stream.Collectors;

public class AdvancedFilterUtil {
    public static final Boolean DEFAULT_KNOWN = Boolean.FALSE;

    public static boolean hasAdvancedFilters(MappingRequest request) {
        if (request == null) return false;

        // Check caddCategories list is non-null and non-empty
        if (request.getCadd() != null && !request.getCadd().isEmpty()) {
            return true;
        }

        // Check amClasses list is non-null and non-empty
        if (request.getAm() != null && !request.getAm().isEmpty()) {
            return true;
        }

        // Check known boolean (assuming default is false, so true means set)
        if (request.getKnown() != null && !request.getKnown().equals(DEFAULT_KNOWN)) {
            return true;
        }

        // Check sort field first
        if (request.getSort() != null && !request.getSort().isEmpty()) {
            return true; // sort set -> filters active

            // Now check order only if sort is set
            // Actually no need to check order here separately because any sort means filters active
            // But if you want to detect when order differs from default, you could refine this:
        }

        // If no sort set, order alone is irrelevant -> ignore order

        // None of the filters set
        return false;
    }

    public static String advancedFilterString(MappingRequest request) {
        if (request == null) return "AdvancedFilter(null)";

        // Sort + dedup caddCategories
        String caddStr = sortedDistinctList(request.getCadd());

        // Sort + dedup amClasses
        String amStr = sortedDistinctList(request.getAm());

        // Known - default to false if null
        Boolean known = request.getKnown();
        String knownStr = (known == null) ? "false" : known.toString();

        // Sort - null or empty treated as null
        String sortStr = (request.getSort() == null || request.getSort().isEmpty()) ? "null" : request.getSort();

        // Order - include only if sort is set (non-null and not "null")
        String orderStr = "null";
        if (!"null".equals(sortStr)) {
            orderStr = (request.getOrder() == null || request.getOrder().isEmpty()) ? "null" : request.getOrder();
        }

        return "AdvancedFilter(" +
                "cadd=" + caddStr +
                ", am=" + amStr +
                ", known=" + knownStr +
                ", sort=" + sortStr +
                ", order=" + orderStr +
                ")";
    }

    public static <T> String sortedDistinctList(java.util.List<T> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return list.stream()
                .filter(e -> e != null)
                .map(Object::toString)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

}
