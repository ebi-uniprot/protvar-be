package uk.ac.ebi.protvar.utils;


import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.SearchTerm;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class DownloadFileUtil {

    /**
     * Download filename format:
     * <prefix>[-fun][-pop][-str][-PAGE-PAGE_SIZE][-ASSEMBLY][-filterHash]
     *
     * Where:
     * - prefix: identifier from search terms (input_id, hash of identifiers, or "all")
     * - fun/pop/str: annotation options (functional/population/structural)
     * - PAGE-PAGE_SIZE: only included if not full download
     * - ASSEMBLY: only if not AUTO
     * - filterHash: short hash of all MappingRequest filters (if any filters applied)
     *
     * Examples:
     * - abc123def456-fun-pop-GRCh38 (input_id with annotations, all pages)
     * - P12345-BRCA1-str-1-50 (identifiers with structural, page 1, size 50)
     * - all-fun-pop-str-a3b2c1 (filter-only with all annotations and filters)
     */
    public static String buildFilename(DownloadRequest request) {
        StringBuilder filename = new StringBuilder();

        // 1. Build prefix from search terms
        String prefix = buildSearchTermsPrefix(request);
        filename.append(prefix);

        // 2. Add annotation options
        if (Boolean.TRUE.equals(request.getFunction())) {
            filename.append("-fun");
        }
        if (Boolean.TRUE.equals(request.getPopulation())) {
            filename.append("-pop");
        }
        if (Boolean.TRUE.equals(request.getStructure())) {
            filename.append("-str");
        }

        // 3. Add pagination info (only if not full download)
        if (!Boolean.TRUE.equals(request.getFull())) {
            filename.append("-").append(request.getPage());
            filename.append("-").append(request.getPageSize());
        }

        // 4. Add assembly (only if not AUTO)
        if (request.getAssembly() != null && !"AUTO".equalsIgnoreCase(request.getAssembly())) {
            filename.append("-").append(request.getAssembly());
        }

        // 5. Add filter hash (if any non-default filters applied)
        if (hasNonDefaultFilters(request)) {
            String filterHash = computeFilterHash(request);
            filename.append("-").append(filterHash);
        }

        return filename.toString();
    }

    /**
     * Check if request has any non-default filters that should affect the filename.
     * This includes all filters and sorting, but excludes default values.
     *
     * DEFAULT VALUES:
     * - known: true (null is treated as true, so only false is non-default)
     * - all others: null/empty (any non-null value is non-default)
     */
    private static boolean hasNonDefaultFilters(DownloadRequest request) {
        // Variant Type - include only if explicitly set to false (non-default)
        if (Boolean.FALSE.equals(request.getKnown())) {
             return true;
        }

        // Functional filters
        if (request.getPtm() != null) return true;
        if (request.getMutagenesis() != null) return true;
        if (request.getConservationMin() != null) return true;
        if (request.getConservationMax() != null) return true;
        if (request.getFunctionalDomain() != null) return true;

        // Population filters
        if (request.getDiseaseAssociation() != null) return true;
        if (request.getAlleleFreq() != null && !request.getAlleleFreq().isEmpty()) return true;

        // Structural filters
        if (request.getExperimentalModel() != null) return true;
        if (request.getInteract() != null) return true;
        if (request.getPocket() != null) return true;
        if (request.getStability() != null && !request.getStability().isEmpty()) return true;

        // Consequence filters
        if (request.getCadd() != null && !request.getCadd().isEmpty()) return true;
        if (request.getAm() != null && !request.getAm().isEmpty()) return true;
        if (request.getPopeve() != null && !request.getPopeve().isEmpty()) return true;
        if (request.getEsm1bMin() != null) return true;
        if (request.getEsm1bMax() != null) return true;

        // Sorting (affects result order, should be part of filename)
        if (request.getSort() != null && !request.getSort().isEmpty()) return true;

        return false;
    }

    /**
     * Build prefix from search terms.
     */
    private static String buildSearchTermsPrefix(DownloadRequest request) {
        if (request.hasNoSearchTerms()) {
            return "all";
        }

        if (request.isInputIdQuery()) {
            return sanitizeForFilename(request.getSearchTerms().get(0).getValue());
        }

        return buildIdentifierPrefix(request.getSearchTerms());
    }

    /**
     * Build prefix from identifier search terms.
     */
    private static String buildIdentifierPrefix(List<SearchTerm> identifiers) {
        if (identifiers.size() == 1) {
            return sanitizeForFilename(identifiers.get(0).getValue());
        }

        // Multiple identifiers - create a combined prefix
        String combined = identifiers.stream()
                .map(SearchTerm::getValue)
                .map(DownloadFileUtil::sanitizeForFilename)
                .sorted() // Sort for consistency
                .collect(Collectors.joining("-"));

        // If combined prefix is too long (>50 chars), use a hash instead
        if (combined.length() > 50) {
            String identifiersStr = identifiers.stream()
                    .map(term -> term.getType().getValue() + ":" + term.getValue())
                    .sorted() // Sort for consistency
                    .collect(Collectors.joining(","));
            return ChecksumUtils.checksum(identifiersStr.getBytes(StandardCharsets.UTF_8))
                    .substring(0, 12);
        }

        return combined;
    }

    /**
     * Compute hash of all filters from MappingRequest.
     * Lists are sorted to ensure consistent hashes regardless of input order.
     * Only non-default values are included.
     *
     * DEFAULT: known=true (null treated as true)
     */
    private static String computeFilterHash(DownloadRequest request) {
        StringBuilder filterStr = new StringBuilder();

        // Variant Type - only include if false (non-default)
        if (Boolean.FALSE.equals(request.getKnown())) {
            filterStr.append("known:false;");
        }

        // Functional filters
        if (request.getPtm() != null) {
            filterStr.append("ptm:").append(request.getPtm()).append(";");
        }
        if (request.getMutagenesis() != null) {
            filterStr.append("mut:").append(request.getMutagenesis()).append(";");
        }
        if (request.getConservationMin() != null) {
            filterStr.append("consMin:").append(request.getConservationMin()).append(";");
        }
        if (request.getConservationMax() != null) {
            filterStr.append("consMax:").append(request.getConservationMax()).append(";");
        }
        if (request.getFunctionalDomain() != null) {
            filterStr.append("funcDom:").append(request.getFunctionalDomain()).append(";");
        }

        // Population filters
        if (request.getDiseaseAssociation() != null) {
            filterStr.append("disease:").append(request.getDiseaseAssociation()).append(";");
        }
        if (request.getAlleleFreq() != null && !request.getAlleleFreq().isEmpty()) {
            filterStr.append("af:").append(sortedDistinctList(request.getAlleleFreq())).append(";");
        }

        // Structural filters
        if (request.getExperimentalModel() != null) {
            filterStr.append("expModel:").append(request.getExperimentalModel()).append(";");
        }
        if (request.getInteract() != null) {
            filterStr.append("interact:").append(request.getInteract()).append(";");
        }
        if (request.getPocket() != null) {
            filterStr.append("pocket:").append(request.getPocket()).append(";");
        }
        if (request.getStability() != null && !request.getStability().isEmpty()) {
            filterStr.append("stability:").append(sortedDistinctList(request.getStability())).append(";");
        }

        // Consequence filters
        if (request.getCadd() != null && !request.getCadd().isEmpty()) {
            filterStr.append("cadd:").append(sortedDistinctList(request.getCadd())).append(";");
        }
        if (request.getAm() != null && !request.getAm().isEmpty()) {
            filterStr.append("am:").append(sortedDistinctList(request.getAm())).append(";");
        }
        if (request.getPopeve() != null && !request.getPopeve().isEmpty()) {
            filterStr.append("popeve:").append(sortedDistinctList(request.getPopeve())).append(";");
        }
        if (request.getEsm1bMin() != null) {
            filterStr.append("esm1bMin:").append(request.getEsm1bMin()).append(";");
        }
        if (request.getEsm1bMax() != null) {
            filterStr.append("esm1bMax:").append(request.getEsm1bMax()).append(";");
        }

        // Sorting (affects result order, should be part of the hash)
        if (request.getSort() != null && !request.getSort().isEmpty()) {
            filterStr.append("sort:").append(request.getSort()).append(";");
            // Only include order if sort is set
            if (request.getOrder() != null && !request.getOrder().isEmpty()) {
                filterStr.append("order:").append(request.getOrder()).append(";");
            }
        }

        // Hash the filter string and return first 6 characters
        byte[] filterBytes = filterStr.toString().getBytes(StandardCharsets.UTF_8);
        String fullHash = ChecksumUtils.checksum(filterBytes);
        return fullHash != null ? fullHash.substring(0, 6) : "000000";
    }

    /**
     * Sort and deduplicate a list for consistent string representation.
     * Preserves the logic from AdvancedFilterUtil.
     */
    private static <T> String sortedDistinctList(List<T> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return list.stream()
                .filter(item -> item != null)
                .map(Object::toString)
                .distinct()
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Sanitize input string for use in filename.
     */
    private static String sanitizeForFilename(String input) {
        if (input == null || input.isEmpty()) {
            return "unknown";
        }
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}