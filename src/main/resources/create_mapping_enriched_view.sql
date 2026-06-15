-- =====================================================
-- CREATE ENRICHED MAPPING VIEW  (BE compatibility bridge)
--
-- Purpose: present the slim Round-B mapping enriched with the dim columns, in the OLD column shape, so the
--   BULK browse/identifier queries (GenomicVariantRepo + MappingRepo.getGenomicVariantsForInput) run on the
--   new schema unchanged. The physical mapping stays slim (normalised); this view reconstructs the legacy
--   columns on demand via cheap PK joins. ZERO storage (it's a view).
--
-- Joins ONLY protein + ensembl_transcript (both PK joins → cannot multiply rows; verified row-for-row equal
--   to the mapping). NO ensembl_gene join — that 2-hop join mis-plans badly (rows=1 estimate → seq-scan of
--   ensembl_gene per row, ~180x slower). reverse_strand is read straight from the mapping fact (m), where
--   the importer keeps it per-row: it is intrinsic to the per-row codon math, and sourcing it from any join
--   (even 1-hop from ensembl_transcript) makes the codon expr depend on that join → the planner defers
--   consequence/mt_aa/score matching to late Join Filters, ~40x on consequence-AA-keyed filter browses.
--
-- TEMPORARY: this is a bridge. It is removed when the bulk queries are rewritten to read slim+dims directly
--   (the deferred resolve→enrich→filter redesign; see doc/mapping_target_architecture.md).
--
-- Per-release: substitute the rel_<release>_ prefix to match application.properties (tbl.prefix). Apply as
--   part of BE release setup, AFTER the importer has produced the mapping + protein + ensembl_transcript
--   tables (mapping must include the reverse_strand column).
-- Maps to property: tbl.mapping.enriched = ${tbl.prefix}_genomic_protein_mapping_enriched
-- =====================================================

CREATE OR REPLACE VIEW rel_2026_02_genomic_protein_mapping_enriched AS
SELECT
    m.id, m.chromosome, m.genomic_position,
    m.base_nucleotide AS allele,        -- legacy name
    m.codon, m.codon_position,
    m.accession, m.protein_position,
    m.amino_acid     AS protein_seq,    -- legacy name
    m.is_match, m.reverse_strand, m.enst, m.enstv, m.ense,
    p.is_canonical, p.gene_name, p.protein_name,
    t.ensg, t.ensp, t.enspv, t.is_mane_select
FROM rel_2026_02_genomic_protein_mapping m
JOIN rel_2026_02_protein p
    ON p.accession = m.accession
JOIN rel_2026_02_ensembl_transcript t
    ON t.accession = m.accession AND t.enst = m.enst;

-- Verify row-for-row parity (view MUST equal the mapping — no rows dropped or multiplied):
--   SELECT (SELECT count(*) FROM rel_2026_02_genomic_protein_mapping)           AS mapping,
--          (SELECT count(*) FROM rel_2026_02_genomic_protein_mapping_enriched)  AS view_enriched;
