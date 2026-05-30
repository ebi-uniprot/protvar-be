package uk.ac.ebi.protvar.dto;

/**
 * One population semantic-search hit. population_text is lean
 * (accession, position, source_text) — the source_text is self-describing
 * (starts with "{accession} {wt}{pos}{alt} {consequence}." and includes any
 * clinical-significance / description / disease text), so v1 returns it
 * as-is for the FE to render. Structured wt/alt/disease fields would need a
 * population_text → population join (no alt column / FK today) — deferred.
 */
public record PopulationVectorSearchResult(
        String accession,
        Integer position,
        String sourceText,
        Double distance
) {}
