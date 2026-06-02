-- Tables used in GenomicVariantRepo
-- This query gives an idea of the size of the tables so we can
-- reorder joins by selectivity (most restrictive first)

SELECT
    relname AS tablename,
    reltuples AS rowcounts,
    pg_relation_size(c.oid) AS tablesize
FROM pg_class c
         JOIN pg_catalog.pg_namespace n
              ON n.oid = c.relnamespace
WHERE relkind='r' AND
    nspname = 'protvarwrite' AND
    relname IN
    (
     'gnomad_allele_freq', 'popeve', 'rel_2025_01_uniprot_refseq', 'rel_2025_01_coding_cadd',
     'alphamissense', 'esm', 'conserv_score', 'rel_2025_01_structure',
     'pocket_v2', 'af2complexes_interfaces', 'afdb_foldx_2025_02', 'rel_2025_01_genomic_protein_mapping'
        )
ORDER BY nspname, reltuples DESC;

-- OUTPUT:
-- tablename	                        rowcounts	tablesize
-- rel_2025_01_coding_cadd	            500288480	29681516544
-- alphamissense	                    216180210	11279925248
-- esm	                                215725230	11256332288
-- afdb_foldx_2025_02	                208785470	14281211904
-- popeve	                            185101390	17292328960
-- rel_2025_01_genomic_protein_mapping	169448260	34743648256
-- gnomad_allele_freq	                52175724	3143106560
-- conserv_score	                    14438161	754319360
-- pocket_v2	                        547401	249626624
-- rel_2025_01_structure	            203212	31391744
-- rel_2025_01_uniprot_refseq	        113717	6160384
-- af2complexes_interfaces	            68756	46661632


-- TODO dbsnp/lookup?