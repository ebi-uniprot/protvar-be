-- =====================================================
-- CRITICAL INDEXES
-- All JOIN and WHERE columns in index key
-- =====================================================

-- Drop old indexes if they exist (optional - check first)
-- DROP INDEX IF EXISTS idx_gnomad_allele_freq_composite;
-- DROP INDEX IF EXISTS idx_popeve_composite;
-- etc.

-- =====================================================
-- CRITICAL: Allele Frequency Indexes
-- =====================================================

-- Primary index: chr, pos, ref, alt (for joins)
-- Adding af for filtering without table lookup
CREATE INDEX CONCURRENTLY idx_gnomad_allele_freq_chr_pos_ref_alt_af
    ON gnomad_allele_freq (chr, pos, ref, alt, af);

-- For allele frequency filtering (af range queries)
CREATE INDEX CONCURRENTLY idx_gnomad_allele_freq_af
    ON gnomad_allele_freq (af);

-- Composite for efficient filtering + join
CREATE INDEX CONCURRENTLY idx_gnomad_allele_freq_af_chr_pos
    ON gnomad_allele_freq (af, chr, pos, ref, alt);

-- =====================================================
-- CRITICAL: PopEVE Indexes
-- =====================================================

-- For joins: refseq_protein, position, wt_aa, mt_aa
CREATE INDEX CONCURRENTLY idx_popeve_refseq_pos_wt_mt
    ON popeve (refseq_protein, position, wt_aa, mt_aa);

-- For filtering by PopEVE score
CREATE INDEX CONCURRENTLY idx_popeve_score
    ON popeve (popeve);

-- Composite: score filtering + join columns
CREATE INDEX CONCURRENTLY idx_popeve_score_refseq_pos
    ON popeve (popeve, refseq_protein, position, wt_aa, mt_aa);

-- =====================================================
-- CRITICAL: UniProt-RefSeq Mapping
-- =====================================================

-- For joins from UniProt to RefSeq
CREATE INDEX CONCURRENTLY idx_rel_2025_01_uniprot_refseq_uniprot
    ON rel_2025_01_uniprot_refseq (uniprot_acc);

-- For joins from RefSeq to UniProt
CREATE INDEX CONCURRENTLY idx_rel_2025_01_uniprot_refseq_refseq
    ON rel_2025_01_uniprot_refseq (refseq_acc);

-- Bidirectional lookup
CREATE INDEX CONCURRENTLY idx_rel_2025_01_uniprot_refseq_both
    ON rel_2025_01_uniprot_refseq (uniprot_acc, refseq_acc);

-- =====================================================
-- CADD Indexes
-- =====================================================

-- For joins: chromosome, position, reference_allele, alt_allele
CREATE INDEX CONCURRENTLY idx_rel_2025_01_coding_cadd_chr_pos_ref_alt
    ON rel_2025_01_coding_cadd (chromosome, position, reference_allele, alt_allele);

-- For filtering by score
CREATE INDEX CONCURRENTLY idx_rel_2025_01_coding_cadd_score
    ON rel_2025_01_coding_cadd (score);

-- Composite for filtering + join
CREATE INDEX CONCURRENTLY idx_rel_2025_01_coding_cadd_score_chr_pos_ref_alt
    ON rel_2025_01_coding_cadd (score, chromosome, position, reference_allele, alt_allele);

-- =====================================================
-- AlphaMissense Indexes
-- =====================================================

-- For joins: accession, position, wt_aa, mt_aa
CREATE INDEX CONCURRENTLY idx_am_accession_pos_wt_mt
    ON alphamissense (accession, position, wt_aa, mt_aa);

-- For filtering by class
CREATE INDEX CONCURRENTLY idx_am_class
    ON alphamissense (am_class);

-- For sorting by pathogenicity
CREATE INDEX CONCURRENTLY idx_am_pathogenicity
    ON alphamissense (am_pathogenicity);

-- Composite for filtering + join
CREATE INDEX CONCURRENTLY idx_am_class_accession_pos
    ON alphamissense (am_class, accession, position, wt_aa, mt_aa);

-- =====================================================
-- ESM1b Indexes
-- =====================================================

-- For joins: accession, position, mt_aa
CREATE INDEX CONCURRENTLY idx_esm_accession_pos_mt
    ON esm (accession, position, mt_aa);

-- For filtering by score
CREATE INDEX CONCURRENTLY idx_esm_score
    ON esm (score);

-- Composite for filtering + join
CREATE INDEX CONCURRENTLY idx_esm_score_accession_pos
    ON esm (score, accession, position, mt_aa);

-- =====================================================
-- Conservation Indexes
-- =====================================================

-- For joins: accession, position, aa
CREATE INDEX CONCURRENTLY idx_conservation_accession_pos_aa
    ON conserv_score (accession, position, aa);

-- For filtering by score
CREATE INDEX CONCURRENTLY idx_conservation_score
    ON conserv_score (score);

-- Composite for filtering + join
CREATE INDEX CONCURRENTLY idx_conservation_score_accession_pos
    ON conserv_score (score, accession, position, aa);

-- =====================================================
-- Structure Indexes
-- =====================================================

-- For PDB lookups
CREATE INDEX CONCURRENTLY idx_rel_2025_01_structure_pdb
    ON rel_2025_01_structure (pdb_id);

-- For accession lookups with position range
CREATE INDEX CONCURRENTLY idx_rel_2025_01_structure_accession_range
    ON rel_2025_01_structure (accession, unp_start, unp_end);

-- Composite
CREATE INDEX CONCURRENTLY idx_rel_2025_01_structure_accession_pdb
    ON rel_2025_01_structure (accession, pdb_id, unp_start, unp_end);

-- =====================================================
-- Pocket Indexes
-- =====================================================

-- For joins on struct_id with residue array
CREATE INDEX CONCURRENTLY idx_pocket_v2_struct_id
    ON pocket_v2 (struct_id);

-- GIN index for array containment queries (@> operator)
CREATE INDEX CONCURRENTLY idx_pocket_v2_residues_gin
    ON pocket_v2 USING gin (pocket_resid);

-- =====================================================
-- Interaction Indexes
-- =====================================================

-- For protein A lookups
CREATE INDEX CONCURRENTLY idx_af2complexes_interaction_a
    ON af2complexes_interaction (a);

-- For protein B lookups
CREATE INDEX CONCURRENTLY idx_af2complexes_interaction_b
    ON af2complexes_interaction (b);

-- GIN indexes for array containment
CREATE INDEX CONCURRENTLY idx_af2complexes_interaction_a_residues_gin
    ON af2complexes_interaction USING gin (a_residues);

CREATE INDEX CONCURRENTLY idx_af2complexes_interaction_b_residues_gin
    ON af2complexes_interaction USING gin (b_residues);

-- =====================================================
-- FoldX (Stability) Indexes
-- =====================================================

-- For joins: protein_acc, position, mutated_type
CREATE INDEX CONCURRENTLY idx_afdb_foldx_2025_02_protein_pos_mt
    ON afdb_foldx_2025_02 (protein_acc, position, mutated_type);

-- For filtering by ddg
CREATE INDEX CONCURRENTLY idx_afdb_foldx_2025_02_ddg
    ON afdb_foldx_2025_02 (foldx_ddg);

-- Composite for filtering + join
CREATE INDEX CONCURRENTLY idx_afdb_foldx_2025_02_ddg_protein_pos
    ON afdb_foldx_2025_02 (foldx_ddg, protein_acc, position, mutated_type);

-- =====================================================
-- Mapping Table Indexes
-- =====================================================

-- For gene lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rel_2025_01_mapping_gene_name
    ON rel_2025_01_genomic_protein_mapping (gene_name);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rel_2025_01_mapping_gene_name_protein_pos
    ON rel_2025_01_genomic_protein_mapping(gene_name, protein_position, codon_position);

-- For accession lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rel_2025_01_mapping_accession
    ON rel_2025_01_genomic_protein_mapping (accession);

-- For Ensembl gene lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rel_2025_01_mapping_ensg
    ON rel_2025_01_genomic_protein_mapping (ensg);

-- For Ensembl transcript lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rel_2025_01_mapping_enst
    ON rel_2025_01_genomic_protein_mapping (enst);

-- For Ensembl protein lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rel_2025_01_mapping_ensp
    ON rel_2025_01_genomic_protein_mapping (ensp);

-- For genomic position lookups (used in filter-first)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rel_2025_01_mapping_chr_pos_ref
    ON rel_2025_01_genomic_protein_mapping (chromosome, genomic_position, allele);

-- =====================================================
-- Analyze tables after index creation
-- =====================================================

ANALYZE gnomad_allele_freq;
ANALYZE popeve;
ANALYZE rel_2025_01_uniprot_refseq;
ANALYZE rel_2025_01_coding_cadd;
ANALYZE alphamissense;
ANALYZE esm;
ANALYZE conserv_score;
ANALYZE rel_2025_01_structure;
ANALYZE pocket_v2;
ANALYZE af2complexes_interaction;
ANALYZE afdb_foldx_2025_02;
ANALYZE rel_2025_01_genomic_protein_mapping;

-- =====================================================
-- Verification Queries
-- =====================================================

-- Check all indexes were created
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename IN (
                    'gnomad_allele_freq', 'popeve', 'rel_2025_01_uniprot_refseq', 'rel_2025_01_coding_cadd',
                    'alphamissense', 'esm', 'conserv_score', 'rel_2025_01_structure',
                    'pocket_v2', 'af2complexes_interaction', 'afdb_foldx_2025_02', 'rel_2025_01_genomic_protein_mapping'
    )
ORDER BY tablename, indexname;

-- Check index sizes
SELECT
    schemaname || '.' || relname AS table,
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'protvarwrite'
  AND relname IN (
    'gnomad_allele_freq', 'popeve', 'rel_2025_01_uniprot_refseq', 'rel_2025_01_coding_cadd',
    'alphamissense', 'esm', 'conserv_score', 'rel_2025_01_structure',
    'pocket_v2', 'af2complexes_interaction', 'afdb_foldx_2025_02', 'rel_2025_01_genomic_protein_mapping'
    )
ORDER BY pg_relation_size(indexrelid) DESC;