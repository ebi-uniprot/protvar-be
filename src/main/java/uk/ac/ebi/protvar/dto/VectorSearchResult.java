package uk.ac.ebi.protvar.dto;

public record VectorSearchResult(String accession, String sourceType, String sourceText, Double distance) {}
