package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.model.DownloadRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class DownloadFileUtil {

    /**
     * Download filename format:
     * <prefix>[-fun][-pop][-str][-PAGE-PAGESIZE][-ASSEMBLY][-filterHash]
     *
     * The prefix encodes the data source so the filename is recognisable:
     *   q        → MD5 of the variant query string (query is arbitrary text, hash keeps it short)
     *   resultId → first 8 chars of the result ID (enough to identify an upload)
     *   ids      → sanitized "type-value" joined by "_" (e.g. uniprot-P22304, gene-BRCA2_pdb-6ioz)
     *   (none)   → "all" (filter-only browse)
     *
     * The filterHash at the end encodes ALL active filter parameters so that two requests
     * with the same source but different filters produce different filenames.
     * Pagination (page/pageSize) is included in the filename directly (omitted when full=true).
     *
     * Same logical request → same filename → existing file served, no regeneration.
     */
    public static String buildFilename(DownloadRequest request) {
        String prefix = buildPrefix(request);

        StringBuilder filename = new StringBuilder(prefix);

        if (Boolean.TRUE.equals(request.getFunction()))  filename.append("-fun");
        if (Boolean.TRUE.equals(request.getPopulation())) filename.append("-pop");
        if (Boolean.TRUE.equals(request.getStructure()))  filename.append("-str");

        if (!Boolean.TRUE.equals(request.getFull())) {
            filename.append("-").append(request.getPage());
            filename.append("-").append(request.getPageSize());
        }

        if (request.getAssembly() != null && !"AUTO".equals(request.getAssembly())) {
            filename.append("-").append(request.getAssembly());
        }

        String filterStr = buildFilterString(request);
        if (filterStr != null) {
            String hash = ChecksumUtils.checksum(filterStr.getBytes(StandardCharsets.UTF_8));
            if (hash != null) filename.append("-").append(hash, 0, 6);
        }

        return filename.toString();
    }

    /**
     * Builds the recognisable prefix from the data source.
     */
    static String buildPrefix(DownloadRequest request) {
        if (request.getQ() != null && !request.getQ().isBlank()) {
            // Variant query: hash it (arbitrary text, keeps filename short)
            String hash = ChecksumUtils.checksum(request.getQ().trim().getBytes(StandardCharsets.UTF_8));
            return hash != null ? hash : "q";
        }
        if (request.getResultId() != null && !request.getResultId().isBlank()) {
            // Uploaded result: first 8 chars of the resultId is enough to identify it
            return sanitizeForFilename(request.getResultId().substring(0, Math.min(8, request.getResultId().length())));
        }
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            // Identifier browse: include type + value so GENE:BRCA2 and UNIPROT:BRCA2 don't collide
            String ids = request.getIds().stream()
                    .map(id -> id.value())
                    .sorted()
                    .map(DownloadFileUtil::sanitizeForFilename)
                    .collect(Collectors.joining("_"));
            return ids;
        }
        return "all"; // filter-only browse
    }

    /**
     * Produces a canonical string of ALL active filter parameters.
     * Returns null if no filters are active.
     * Lists are sorted + deduplicated so order doesn't matter.
     */
    static String buildFilterString(DownloadRequest request) {
        StringBuilder sb = new StringBuilder();

        appendVal(sb, "known", request.getKnown());
        appendSorted(sb, "cadd", request.getCadd());
        appendSorted(sb, "am", request.getAm());
        appendSorted(sb, "popeve", request.getPopeve());
        appendSorted(sb, "stability", request.getStability());
        appendSorted(sb, "alleleFreq", request.getAlleleFreq());
        appendVal(sb, "consMin", request.getConservationMin());
        appendVal(sb, "consMax", request.getConservationMax());
        appendVal(sb, "esm1bMin", request.getEsm1bMin());
        appendVal(sb, "esm1bMax", request.getEsm1bMax());
        appendVal(sb, "domain", request.getFunctionalDomain());
        appendVal(sb, "expModel", request.getExperimentalModel());
        appendVal(sb, "interact", request.getInteract());
        appendVal(sb, "pocket", request.getPocket());
        appendVal(sb, "ptm", request.getPtm());
        appendVal(sb, "mutagen", request.getMutagenesis());
        appendVal(sb, "disease", request.getDiseaseAssociation());
        appendVal(sb, "sort", request.getSort());
        appendVal(sb, "order", request.getOrder());

        return sb.length() > 0 ? sb.toString() : null;
    }

    private static void appendVal(StringBuilder sb, String key, Object val) {
        if (val != null) {
            if (sb.length() > 0) sb.append(",");
            sb.append(key).append("=").append(val);
        }
    }

    private static <T> void appendSorted(StringBuilder sb, String key, List<T> list) {
        if (list != null && !list.isEmpty()) {
            String sorted = list.stream()
                    .filter(e -> e != null)
                    .map(Object::toString)
                    .distinct().sorted()
                    .collect(Collectors.joining("|"));
            if (sb.length() > 0) sb.append(",");
            sb.append(key).append("=[").append(sorted).append("]");
        }
    }

    public static String sanitizeForFilename(String input) {
        if (input == null || input.isEmpty()) return "unknown";
        return input.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }
}
