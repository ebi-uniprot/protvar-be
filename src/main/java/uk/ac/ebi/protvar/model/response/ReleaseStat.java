package uk.ac.ebi.protvar.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One figure for {@code GET /stats}: what it is ({@code release_stats_key}) and its latest value for the release.
 * Keys are {@code <domain>.<what>[.<sub>]}; {@code a.b.<sub>} is a sub-figure of {@code a.b}. {@code inSync} is
 * false when the counted table is not one the BE serves (e.g. counted on cosmic_v99 while the BE reads
 * cosmic_v103); null when the figure spans several tables.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReleaseStat(
        String key,
        String label,
        String description,
        long value,
        String source,
        String version,
        String sourceTable,
        Instant recorded,
        Boolean inSync
) {}
