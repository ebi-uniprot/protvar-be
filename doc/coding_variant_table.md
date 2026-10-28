# Coding variant table — precomputed per-release filter/sort table

> Status: PROPOSAL (2026-10-27), for decision. Companion to `mapping_target_architecture.md` (target
> pipeline) and `mapping_current_state.md` (as-is). Numbers measured on prod `unpprotvar16pro`, rel 2026_02.

## 1. Problem

Filter-only browse builds every result at query time: take a "lead" set, join the 266M-row slim mapping,
expand 3 alts, compute the consequence AA in SQL (`rna_base_for_strand` + `codon_table`), then join each
score/feature table. It is slow or failing for many filter combinations, and no query rewrite fixes it:

| request | int today (enriched view) | best rewrite tried (slim ⋈ protein) |
|---|---|---|
| `interact` | 4.5 s | 10.5 s |
| `interact + cadd ≥ 30` | 500 after 62 s | 10.6 s |
| `interact + domain` | > 10 min (killed) | 10.7 s |
| `known + AF rare + conservation` | — | > 20 s (timeout) |
| any score sort (`pocket + sort am`, `known + sort cadd`) | — | > 20 s (timeout) |

Why rewrites can't fix it:

- **No lead is selective.** The validator's "driver" filters were sized by table rows, but they expand to
  residues: pocket 5.7M, interface 2.8M, structure **68.9M**, coding dbSNP 20.3M (CADD ≥ 30: 7.6M). Every
  browse is an intersection of multi-million-row sets plus per-row codon math.
- **Sorting by score** needs every candidate scored before page 1.
- **The slim mapping has ~3.7 rows per canonical position** (one per Ensembl transcript; chr21: 1.17M
  canonical rows, 320k distinct positions). Browse SQL doesn't de-duplicate them, so it does ~3.7× the work
  and can return the same variant twice on a page.

## 2. Proposal

Name: `coding_variant`, matching the other coding-position reductions (`coding_region`, `coding_cadd`,
`coding_dbsnp`). It holds every possible coding SNV and serves browse, uploads (Annotate) and display alike;
"annotation" is avoided because it already names the function/population/structure tabs.

Build once per release a table with **one row per mapped genomic variant per gene**: every mapped coding
position × its 3 alts, on the gene's lead accession, carrying every filterable and sortable attribute
precomputed. Browse becomes a single-table query: `WHERE <flags and ranges> [ORDER BY <score>] LIMIT n`.

```
rel_<R>_coding_variant        (~100M rows; ~33M mapped (position, gene) × 3 alts)
  chromosome     varchar(2)   genomic (GRCh38)
  position       int
  ref, alt       char(1)
  accession      varchar(15)  the gene's lead accession at this position (lead rule below)
  is_canonical   bool         lead is the canonical (false = position only on non-canonical isoforms)
  protein_pos    int
  codon_pos      smallint
  wt_aa, mt_aa   char(1)      mt_aa precomputed ('*' stop)
  -- scores (null = no score)
  cadd           real         genomic_score.score
  avi            real         genomic_score.avi_score
  am             real         alphamissense.am_pathogenicity   am_class smallint
  popeve         real         via uniprot_refseq
  esm1b          real
  conservation   real
  af             real         gnomAD
  foldx_ddg      real
  -- flags (residue- or variant-level)
  known          bool         coding dbSNP has this alt
  pocket, interface, exp_structure, disease  bool
  ptm, mutagen, domain, binding, actsite, transmem  bool   (FeatureGroup, same rules as today)
```

Distinct by construction on `(chromosome, position, ref, alt, accession)`. The transcript doesn't matter
for filtering, so de-duplication happens at build.

**No mapping is lost.** Rows come from the mapping, not from the scores, and from **every mapped position**,
not only canonical ones:

- **Non-canonical-only positions are kept.** 7% of mapped positions have no canonical mapping at all
  (chr21: 23,733 of 341,120, e.g. exons used only by an alternative isoform; 8,441 of them via `-N` isoforms).
  They get rows on their gene's lead accession (lead rule below), with `is_canonical = false`. A query can
  keep today's canonical-only browse with `WHERE is_canonical`.
- **Scores and annotations are optional.** Scores are `LEFT JOIN`ed (missing = `null`), flags default
  `false`, and positions with an incomplete codon/AA keep their row with `mt_aa = null`. Non-canonical leads
  still get the genomic attributes (CADD, AVI, known, AF). They have almost no protein-level scores
  (checked 2026-10-28): no score table has a `-N` isoform accession, and only 7 (AM) / 30 (ESM) /
  32 (conservation) / 28 (popEVE via RefSeq) non-canonical accessions have scores, against ~18.6k canonical
  each.
- **Only the per-transcript repeats go.** Same genomic variant on the same lead accession; the other
  isoforms and transcripts still come from the mapping at display, and unfiltered identifier browse keeps
  listing every isoform from the mapping.

**Build check:** fail unless row count = Σ over distinct mapped `(chromosome, position, gene)` of the
position's alts (3), i.e. no mapped position is missing.

### Lead rule (one accession per position and gene)

Per `(chromosome, position, gene)`, the lead accession is the first of:

1. the **canonical** accession, if the gene has one at this position;
2. else an **isoform of a reviewed entry**: an accession with a `-N` suffix, in natural accession order;
3. else the rest (unreviewed entries), in natural accession order.

There is no reviewed flag in our tables, and reviewed ≠ canonical (P18583-4 is a reviewed SON isoform but
not canonical). By UniProt convention only reviewed (Swiss-Prot) entries have `-N` isoforms, so the suffix
is the test. Checked 2026-10-28 on rel 2026_02: of 25,449 `-N` accessions, 25,037 have a canonical base
accession in `protein`, 25 a non-canonical base (likely an `is_canonical` import quirk, to check), and 387 a
base missing from `protein` (canonical not mapped this release). The suffix covers all of them; a
"base is canonical" lookup would miss 412. MANE doesn't help here: MANE Select is per transcript and usually
the canonical's transcript, so where there's no canonical (the 7% case), MANE is missing too.

Example (chr21:33572551, SON, no canonical): mapped by H7C257 (unreviewed) and P18583-4 (reviewed isoform).
Today's sort (canonical, then accession order) leads with H7C257 only because H < P; the rule above leads
with **P18583-4**.

The same rule must replace the lead choice in the result list (`Isoform.compareTo`, used by
`IsoformConverter`) and `upload_variant` (`UploadFilterService.LEAD_ORDER`), so all three lead with the same
accession and the uploads join (§5) stays exact.

**Why not a row per accession.** Rows for every accession, not just the lead, would be
`(position × accession × alt)`: chr21 has 1.26M (position, accession) pairs → ~3.8M rows instead of 1.05M,
**~360M rows / ~100 GB genome-wide instead of ~100M**. The extra rows would add almost nothing: non-lead
accessions have no protein-level scores (above), UniProt features, pockets, interfaces and structures are
annotated on the canonical sequence, and the genomic attributes (CADD, AVI, known, AF) repeat the lead's.
What they do have is their own protein position (P18583-4 is at residue 2296, H7C257 at 68). That only
matters when browsing by that accession itself: resolve it to its gene's positions (§4). Every accession and
transcript is still shown on the result page from the mapping.

### Indexes

| index | serves |
|---|---|
| unique `(chromosome, position, ref, alt, accession)` | point lookups, uploads join (§5) |
| `(accession, protein_pos)` | identifier browse (§4), protein-ordered default sort |
| btree on each sortable score: `cadd`, `avi`, `am`, `popeve`, `esm1b` | `ORDER BY score LIMIT n` walks the index and stops early |
| partial btree per flag, e.g. `(accession, protein_pos) WHERE pocket` | lead / bitmap-AND for flag combinations |

The planner can bitmap-AND flag partial indexes with score-range indexes for combinations, and walk a score
index for sorts. Neither works today, because the attributes are spread over separate tables.

**Score sorts show only variants that have that score** (decided 2026-10-28). The query adds
`<score> IS NOT NULL` and orders by the score alone, so the planner reads the default btree in order
(backwards for DESC) and stops after one page. The FE notes that unscored variants are hidden while sorting.
Without the `IS NOT NULL`, `DESC NULLS LAST` doesn't match a default index and Postgres sorts every
candidate (chr1: 0.6–0.8 s → genome ~7 s). With it: 0.08–7 ms on chr1, including page 50. Today's browse
puts unscored variants first on a DESC sort (Postgres default), which was never intended.

### Size (estimate)

From the mapping itself: a mapping row is one (position, accession, transcript), with no alt; a browse row
is one (position, gene, alt). On chr21 that's 2,834,835 mapping rows over 341,120 positions (3.7 accessions
× 2.25 transcripts per position) and 350,042 (position, gene) pairs. So browse rows = 350k × 3 ≈ 1.05M,
**~37% of the mapping**. Genome-wide: 266.6M × 0.37 ≈ **~100M rows**.

~100M rows × ~100 B ≈ **10 GB** heap; unique + accession indexes ≈ 7 GB; 5 score indexes ≈ 10–12 GB;
partial flag indexes a few GB (structure is the large one, ~67% of rows). **≈ 30 GB total**, about the
size of the 2026_02 mapping table (26 GB + 23 GB indexes). The prototype (§7) will give the real figure.

## 3. Build (protvar-production)

A new finalise step, after the score and annotation tables are loaded (runs on prod as `protvarwrite`;
hours are fine):

1. `lead_positions`: from slim mapping ⋈ `protein` ⋈ `ensembl_transcript` (for the gene; fine at build
   time), one row per `(chromosome, genomic_position, gene)` on the lead accession (lead rule, §2), keeping
   codon, codon_position, reverse_strand, amino_acid, is_canonical.
2. × 3 alts → `mt_aa` via the existing SQL codon functions (same as browse today, now once per release).
3. `LEFT JOIN` each score table on its own key; flags via `EXISTS` / joins on the feature residue sets
   (same definitions as `GenomicVariantRepo` / `MappingRepo` today).
4. `CREATE TABLE … AS` (UNLOGGED during build, then `ALTER … SET LOGGED`), indexes, `ANALYZE`, and grant to
   `k8sprotvarro` in `db_finalise.sql`. Delphix copies get it with the next refresh.

Unprefixed score tables (`alphamissense`, `esm`, `popeve_2025_07`, …) are read at build. A newer score
version means a rebuild, which already happens every release. This fits the parked `protein_score` /
`genomic_score` consolidation: those can become build inputs.

## 4. BE changes

- **Filter-only browse:** `GenomicVariantRepo` (strategies, codon SQL, ~600 lines) is replaced by one query
  builder over `rel_<R>_coding_variant`. The driver/validator rule can be relaxed. A cap and the 20 s page
  timeout stay as a safety net.
- **Identifier browse with filters:** the identifier resolves to accessions (UniProt/gene/PDB/RefSeq/Ensembl),
  then the same query runs with `accession = ANY(...)`. **Open:** today `ids[]` returns *all isoforms* with
  filters applied per isoform row; this table has one lead accession per position and gene. An identifier
  naming a non-canonical isoform that isn't the lead (its gene has a canonical) matches no row by
  accession. Either resolve it to its gene's positions, or keep the current `MappingRepo` path for that
  case.
- **Unfiltered identifier browse** keeps `MappingRepo`, which lists every isoform.
- **Display:** the page's genomic variants still go through `InputMapper`. The mapping lookup (full
  isoform/transcript tree) and `GeneConverter`/`IsoformConverter` stay. **Score attachment simplifies:** the
  separate batch fetches in `loadCoreMappingAndScores` (`GenomicScoreRepo` for CADD/AVI, `ScoreNewRepo` for
  AM/popEVE) and their key matching become one batch lookup of the page's browse rows, with every score on
  the row. Non-canonical isoforms show no AM/popEVE today either, so nothing changes there.

## 5. Uploads (filters on user inputs)

`upload_variant` keeps its job (line numbers, the user's derived variants) but no longer needs to evaluate
filters itself: filters join `upload_variant` to `rel_<R>_coding_variant` on the unique key and test columns.
`UploadVariantRepo.where()` and browse then share **one** filter builder, so the third copy of the filter rules
goes away. Both tables use the same lead rule, so every `upload_variant` row has its browse row.

## 6. Alternatives considered

- **A. Selectivity-ranked lead picker** (target-architecture §3): no data change, but every lead is millions
  of rows. It speeds up dense combinations, not sparse intersections or score sorts.
- **Solr** (what the UniProt website does: Solr for search/filter/sort, Voldemort as entry store, no direct DB
  access from the site). The best fit for this access pattern: each filter is a cached bitset and combinations
  are bitset ANDs (sparse intersections stay fast, B's weak spot in Postgres); score sorts read per-field sort
  values and keep the top n; exact counts and facet counts are cheap (no "10,000+"/unknown totals; facets could
  drive the FE filter panel); 100M docs × ~25 fields is routine. Costs: a Solr deployment on our k8s clusters
  (pub/fallback/dev, ~20–40 GB index), a per-release indexing step kept in step with releases and Delphix
  refreshes, and a second datastore for a small team. The UniProt BE team has the expertise (`uniprot-infra`
  charts). **B's table is exactly the document Solr would index**, so B is step 1 either way. See the
  decision point in §7.
- **Columnar engine** (DuckDB/Parquet beside Postgres): also suits this access pattern, but new infra, a sync
  step and another runtime. Solr has in-house support at EBI. A columnar engine doesn't.
- **Materialised view:** same storage as a table but less control over build/index/grant steps. A plain CTAS
  in the release pipeline matches how `coding_cadd` / `genomic_score` are built.

## 7. Rollout

1. Prototype the build SQL on prod for one chromosome; measure build time, size, and the query timings
   above against it, including the hard cases: sparse intersections, score sorts, exact counts.
2. **Decision point: Postgres or Solr.** Bar: page 1 under ~2 s for every filter/sort combination, with a
   usable count. If Postgres meets it, continue below with no new infra. If sparse combinations or counts
   miss it, index the same table into Solr: the table build becomes the indexer's input, and the BE
   queries Solr for page keys and counts instead of SQL. Steps 3–6 apply either way; only the query backend
   differs.
3. Full build as a protvar-production finalise step, plus a manifest entry in `docs/release-tables.md`.
4. BE: new `CodingVariantRepo` behind a property (`browse.source=table|legacy`); parity-check against legacy
   on dense cases (row sets, de-duplicated), then switch and delete `GenomicVariantRepo`.
5. Uploads join (§5), then remove the duplicated filter rules.
6. Drop the `_enriched` view at the next release rollover (nothing reads it after the BE PR that moves the
   bulk queries off it).

## 8. Open questions

- Filter-only browse: include the ~7% non-canonical-only positions by default (no mapping hidden), or keep
  today's canonical-only behaviour with `WHERE is_canonical`?
- Identifier browse with filters naming a non-lead isoform (§4): resolve to its gene's positions, or keep
  the `MappingRepo` path?
- Lead rule (§2): change the result list and `upload_variant` to "canonical → `-N` isoform → rest" ahead of
  the table (small, independent), or together with it?
- Keep a default order (e.g. `accession, protein_pos` via its index)? Cheap here, unlike today.
- Include synonymous (`mt_aa = wt_aa`)? Today's browse includes them (3 alts, no consequence filter), so
  yes for parity. Consequence could become a filter.
- Build placement: after `genomic_score`/AVI and annotation loads; needs the feature tables final.
