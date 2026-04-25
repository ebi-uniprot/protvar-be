package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.model.MappingRequest;

import java.util.Optional;

/**
 * Validates that a MappingRequest has at least one "driver" — a field that
 * bounds the leading table of the resulting query to a manageable size.
 *
 * Without a driver, the dispatcher would attempt a full scan of the
 * ~169M-row mapping table, which is not safe to run.
 *
 * Drivers (any one is sufficient):
 *   - q                     (variant query, single-row lookup)
 *   - resultId              (cached upload, bounded by uploaded input size)
 *   - ids[] non-empty       (identifier-anchored union, Strategy 1)
 *   - pocket = true         (leads from pocket_v2,            ~547K rows)
 *   - interact = true       (leads from af2complexes_interfaces, ~68K rows)
 *   - experimentalModel     (leads from rel_*_structure,      ~203K rows)
 *   - known = true          (leads from mapping_dbsnp_lookup,  ~15M rows)
 *
 * All other filters (cadd, am, popeve, esm1b, stability, alleleFreq,
 * conservation, startPos/endPos, sort) are refinements only — they cannot
 * stand alone because their underlying tables are 14M–500M rows and
 * lead to unbounded scans of the mapping table without a driver.
 */
public class MappingRequestValidator {

    public static final String NO_DRIVER_MESSAGE =
            "Please provide at least one of: a variant query (q), an uploaded result ID (resultId), " +
            "an identifier (ids), or a primary filter (pocket, interact, experimentalModel, known). " +
            "Other filters (CADD, AM, popEVE, ESM1b, stability, allele frequency, conservation) refine " +
            "these but cannot stand alone.";

    private MappingRequestValidator() {}

    /**
     * @return true if the request has at least one driver — safe to dispatch.
     */
    public static boolean hasDriver(MappingRequest request) {
        if (request == null) return false;

        if (request.getQ() != null && !request.getQ().isBlank()) return true;
        if (request.getResultId() != null && !request.getResultId().isBlank()) return true;
        if (request.getIds() != null && !request.getIds().isEmpty()) return true;

        if (Boolean.TRUE.equals(request.getPocket())) return true;
        if (Boolean.TRUE.equals(request.getInteract())) return true;
        if (Boolean.TRUE.equals(request.getExperimentalModel())) return true;
        if (Boolean.TRUE.equals(request.getKnown())) return true;

        return false;
    }

    /**
     * @return Optional.empty() if valid, otherwise an error message suitable
     *         for a 400 Bad Request response body.
     */
    public static Optional<String> validate(MappingRequest request) {
        if (!hasDriver(request)) {
            return Optional.of(NO_DRIVER_MESSAGE);
        }
        return Optional.empty();
    }
}
