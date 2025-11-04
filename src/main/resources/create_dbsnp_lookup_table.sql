-- =====================================================
-- CREATE dbSNP LOOKUP TABLE
--
-- Purpose: Reduce dbSNP joins from ~1B rows to ~15M rows
-- Run time: ~10-15 mins
-- TODO: Verify table names match the application.properties
-- =====================================================

-- Step 1: Create the lookup table
-- This joins mapping with dbsnp once, storing only relevant positions
CREATE TABLE mapping_dbsnp_lookup AS
SELECT DISTINCT
    m.chromosome as chr,
    m.genomic_position as pos,
    m.allele as ref,
    string_to_array(d.alt, ',') as known_alts
FROM rel_2025_01_genomic_protein_mapping m
INNER JOIN dbsnp_b156 d ON
    d.chr = m.chromosome AND
    d.pos = m.genomic_position AND
    d.ref = m.allele;

-- Check initial size
SELECT
    pg_size_pretty(pg_total_relation_size('mapping_dbsnp_lookup')) as table_size,
    COUNT(*) as row_count,
    COUNT(DISTINCT chr) as chromosomes,
    AVG(array_length(known_alts, 1)) as avg_alts_per_position
FROM mapping_dbsnp_lookup;

-- Step 2: Create indexes
-- Primary lookup index (most important)
CREATE INDEX idx_mapping_dbsnp_chr_pos_ref
    ON mapping_dbsnp_lookup (chr, pos, ref);

-- Secondary index for range queries
CREATE INDEX idx_mapping_dbsnp_chr_pos
    ON mapping_dbsnp_lookup (chr, pos);

-- Chromosome-only index (for region queries)
CREATE INDEX idx_mapping_dbsnp_chr
    ON mapping_dbsnp_lookup (chr);

-- Step 3: Analyze for query planning
ANALYZE mapping_dbsnp_lookup;

-- Step 4: Verify indexes were created
SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'mapping_dbsnp_lookup'
ORDER BY indexname;

-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================

-- Check distribution by chromosome
SELECT
    chr,
    COUNT(*) as positions,
    pg_size_pretty(SUM(pg_column_size(known_alts))) as array_size
FROM mapping_dbsnp_lookup
GROUP BY chr
ORDER BY chr;

-- Check some sample data
SELECT * FROM mapping_dbsnp_lookup
WHERE chr = '17' AND pos BETWEEN 43000000 AND 43100000
    LIMIT 10;

-- Compare with original dbsnp query (count difference is expected)
-- Test case: BRCA1 region
-- Only coding region variants in your mapping
SELECT COUNT(*) as lookup_count
FROM mapping_dbsnp_lookup
WHERE chr = '17' AND pos BETWEEN 43044295 AND 43170245;

-- ALL variants (coding + non-coding)
SELECT COUNT(*) as original_count
FROM dbsnp_b156
WHERE chr = '17' AND pos BETWEEN 43044295 AND 43170245;

-- =====================================================
-- PERFORMANCE TEST
-- =====================================================

-- Test query performance comparison
-- Test 1: Old approach (joining full dbsnp)
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM rel_2025_01_genomic_protein_mapping m
         CROSS JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS alt(alt_allele)
         INNER JOIN dbsnp_b156 d ON
    d.chr = m.chromosome AND
    d.pos = m.genomic_position AND
    d.ref = m.allele AND
    alt.alt_allele = ANY(string_to_array(d.alt, ','))
WHERE m.gene_name = 'BRCA1'
  AND alt.alt_allele <> m.allele;

-- Test 2: New approach (using lookup table)
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM rel_2025_01_genomic_protein_mapping m
         CROSS JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS alt(alt_allele)
         INNER JOIN mapping_dbsnp_lookup dbsnp ON
    dbsnp.chr = m.chromosome AND
    dbsnp.pos = m.genomic_position AND
    dbsnp.ref = m.allele AND
    alt.alt_allele = ANY(dbsnp.known_alts)
WHERE m.gene_name = 'BRCA1'
  AND alt.alt_allele <> m.allele;

-- Expected improvement: 10-50x faster