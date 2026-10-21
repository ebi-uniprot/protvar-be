package uk.ac.ebi.protvar.model.response;

/**
 * How much annotation ProtVar has at a protein position, per annotation tab (0–1), precomputed in the release
 * (protvar-production release/richness). The UI shades the three annotation icons with it.
 */
public record Richness(double fun, double pop, double str) {}
