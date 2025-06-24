package uk.ac.ebi.protvar.utils;


import uk.ac.ebi.protvar.model.DownloadRequest;

import java.nio.charset.StandardCharsets;

public class DownloadFileUtil {

    /**
     * Download filename format:
     * <prefix>[-fun][-pop][-str][-PAGE][-PAGE_SIZE][-ASSEMBLY][-filterHash]
     *
     * Depending on the input type, prefix is `request.input` e.g. input id, protein acc, etc.
     * if input tupe is null, it is assumed to be a single variant input, and a checksum
     * is used as standard user input.
     *
     * todo: maybe use sanitized single variant input as prefix if type==null
     *
     * todo: null type shouldn't be assumed to be single variant, we should add a SINGLE_VARIANT type to clearly
     *   indicate this. no input may mean search without input but with filters.
     */
    public static String buildFilename(DownloadRequest request) {
        String prefix = request.getType() == null
                ? ChecksumUtils.checksum(request.getInput().getBytes(StandardCharsets.UTF_8))
                : sanitizeForFilename(request.getInput());

        StringBuilder filename = new StringBuilder(prefix);
        if (Boolean.TRUE.equals(request.getFunction())) filename.append("-fun");
        if (Boolean.TRUE.equals(request.getPopulation())) filename.append("-pop");
        if (Boolean.TRUE.equals(request.getStructure())) filename.append("-str");

        if (!Boolean.TRUE.equals(request.getFull())) {
            filename.append("-").append(request.getPage());
            filename.append("-").append(request.getPageSize());
        }

        if (request.getAssembly() != null && !"AUTO".equals(request.getAssembly())) {
            filename.append("-").append(request.getAssembly());
        }

        if (AdvancedFilterUtil.hasAdvancedFilters(request)) {
            String advancedFilterString = AdvancedFilterUtil.advancedFilterString(request);
            String hash = ChecksumUtils.checksum(advancedFilterString.getBytes(StandardCharsets.UTF_8));
            String shortHash = hash != null ? hash.substring(0, 6) : "";
            filename.append("-").append(shortHash);
        }

        return filename.toString();
    }


    public static String sanitizeForFilename(String input) {
        if (input == null || input.isEmpty()) {
            return "unknown";
        }
        // Replace characters that are problematic in filenames
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

}
