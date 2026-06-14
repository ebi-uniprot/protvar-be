# Mapping & advanced-search — current-state map (FE → BE), and DB-vs-user-input analysis

Foundation document: the COMPLETE current behaviour of the variant-mapping + advanced-search feature,
written before any redesign so the design decisions follow from the full picture (not the other way round).
Two questions drive it: (1) what is each part of the pipeline, end to end; (2) for every field/decision,
**is it driven by what the USER typed, or by the DATABASE?** — because cleanly separating those is where the
simplification lives.

> Status: DESCRIPTIVE (as-is). No design decisions here. Companion to advanced_search_query_design.md
> (which should be reconciled to this once the picture is agreed).

---

## 1. Two request families (one dispatcher)

`MappingController` → `MappingService.get()` → `getInputs()` dispatches by request shape
(`MappingService.java:71-89`), after `MappingRequestValidator` enforces a "driver"
(`MappingRequestValidator.hasDriver`):

| request shape | source of the variant LIST | how alts arise | counting |
|---|---|---|---|
| `q=…` (one variant string) | `VariantParser` inline | from the user string (or generated) | exact = 1 |
| `resultId=…` (cached upload) | `UploadCacheService` → `VariantParser` | per uploaded line | exact (upload size) |
| `ids[]=…` (UniProt/Gene/PDB/Ensembl/RefSeq) | **`MappingRepo.getGenomicVariantsForInput`** (CTE) | generated (3 alts) in SQL | exact |
| filter-only browse | **`GenomicVariantRepo.get`** (4 strategies) | generated (3 alts / dbsnp known) in SQL | capped (10k) + 3s timeout |

After the list exists, **one enrichment pipeline** runs for `q`/`resultId` inputs
(`InputMapper.getMapping` → `preprocess` → `loadCoreMappingAndScores` → `GeneConverter`/`IsoformConverter`).
The `ids[]` and filter-browse paths build their annotated rows **inside their own SQL** instead.

## 2. End-to-end journey (FE calls → BE)

1. **Submit search** — FE `POST /mapping` (filters/ids) or `GET /mapping?q=` ; file/text → `POST /input/{file,text}` → `inputId` → `GET /mapping/{resultId}`.
2. **Result list** — `PagedMappingResponse`: `content.inputs[].derivedGenomicVariants[].genes[].isoforms[]`; `totalItems` (exact / `cap+1` / `-1` unknown), `totalCap`, `last`. Pagination `page/pageSize`.
3. **Sort / change filters** — same `POST /mapping`, re-fired, `page=1`.
4. **Expand isoforms (lazy)** — `GET /mapping?q=<chr-pos-ref-alt>` (single-variant) → full isoform list; axios-cache de-dupes.
5. **Annotation panels (lazy, per variant)** — URIs embedded in each `Isoform`:
   `referenceFunctionUri` → `GET /function/{acc}/{pos}`; `populationObservationsUri` → `GET /population/{acc}/{pos}`;
   `proteinStructureUri` → `GET /structure/{acc}/{pos}`; plus `/prediction/{foldx,pocket,interaction}`, `/score`, `/allelefreq`.
6. **Download** — `POST /download` (same request + `function/population/structure/full/email`), async, chunked stream.

The **list response is lean** (coords + canonical isoform + a couple of scores + annotation URIs); everything
heavy is fetched lazily by URI. The FE already renders a **canonical lead row + lazy alternate isoforms**.

## 3. THE KEY TABLE — what drives each field (USER input vs DB)

For a fully-resolved variant, by input mode. **USER** = came from what the user typed; **DB** = from the
database; **DERIVED** = computed (codon math) from DB codon + the alt allele.

| resolved field | genomic input (`19-1010539-G-C`) | protein input (`P05067 M1L`) | cDNA HGVS | variant ID (rs…) | filter browse |
|---|---|---|---|---|---|
| chromosome | **USER** | DB | DB | DB (lookup) | DB |
| genomic position | **USER** | DB | DB | DB | DB |
| ref allele (base) | **USER** (DB if omitted) | DB | USER→checked vs DB | DB | DB |
| **alt allele** | **USER** (or all-3 generated if omitted) | **DERIVED**: the alt(s) whose codon yields the user's AA — or all-3 if no AA given | USER | DB (the variant) | **GENERATED** (all-3, or dbsnp `known_alts`) |
| accession | DB | **USER** | DB (via transcript) | DB | DB |
| protein position | DB | **USER** | DB | DB | DB |
| codon, codon_position | DB | DB | DB | DB | DB |
| reverse_strand | DB (cache/transcript) | DB | DB | DB | DB |
| ref AA | DB | USER(optional)+DB-checked | DB | DB | DB |
| **alt AA (consequence)** | **DERIVED** | USER(optional) / **DERIVED** | **DERIVED** | **DERIVED** | **DERIVED** |
| is_canonical / gene_name / ensp / ensg / mane | DB (cache) | DB (cache) | DB | DB | DB (canonical via filter) |
| CADD/AM/popEVE/ESM/conservation, function/population/structure | DB | DB | DB | DB | DB (some as filters) |

**Takeaways:**
- The ONLY user-driven things are: the input coordinates/identifier, and OPTIONALLY a ref/alt **allele**
  (genomic/cDNA) or a ref/alt **AA** (protein). *Everything else is DB-driven.*
- The **alt allele** is the single messiest field — four different origins (user allele / user-AA-derived /
  all-3 generated / dbsnp-known) → forked code across `GenomicInput` parse, `Pro2Gen`, `MappingRepo`,
  `GenomicVariantRepo`.
- The **codon→AA** bridge (DERIVED) is needed in *every* mode (for display + because scores are keyed by the
  mutant AA) and is currently implemented **twice**: Java (`Codon`, input path) and SQL
  (`rna_base_for_strand`+`codon_table`, browse/ids path).
- The dim attributes (canonical/strand/gene_name/ensp/ensg/mane) are now **all cache-driven** (Round B
  cut-over) — no longer mapping columns.

## 4. Why two machineries exist (the divergence)

| | input-driven (`q`/`resultId`) | browse / `ids[]` |
|---|---|---|
| variant list | parse user input | generate from DB |
| consequence AA | **Java** (`Codon.altCodon`/`altAA`, `IsoformConverter`/`Pro2Gen`) | **SQL** (`rna_base_for_strand`+`codon_table`) |
| scores/annotations | **batch-fetched by key** then attached (`loadCoreMappingAndScores`, `ScoreNewRepo.getMappingScores`) | **joined inline in SQL** (`am.mt_aa=c.amino_acid`, etc.) |
| filter/sort/paginate by score | N/A (input isn't filtered by score) | **yes** — the whole reason it's a SQL mega-join |
| dim fields | cache (`createMapping`) | inline columns today → must move to cache/joins |

They do the **same conceptual job** (produce annotated variants) by **opposite means**. The browse path is
SQL-heavy *only because it filters/sorts/paginates by score in-query*; the input path is the cleaner,
Java-enrich-after-batch model.

## 5. Simplification candidates (for discussion — NOT decided)

1. **Unify on one consequence routine** — Java `Codon` (done: `rnaBaseForStrand`/`altCodon`/`altAA`,
   unit-tested + DB-parity-checked; `Pro2Gen` migrated). The SQL copy stays only where the browse path needs
   the AA as a join key.
2. **Unify alt-allele resolution** — one function: *(user allele) | (derive from user AA) | (all-3) |
   (dbsnp known)* → returns the alt set + per-alt consequence. Removes the fork across 4 classes.
3. **Collapse the two machineries** — make browse generate candidate variants (lead from the most-selective
   filter, paginated in SQL) then run them through the **same Java enrich-after-batch** pipeline as the input
   path. This would delete `GenomicVariantRepo`'s mega-join and the SQL codon entirely.
   - *Blocker to resolve:* filtering/sorting/paginating by score. Works cleanly when the score IS the lead
     (e.g. filter AM-pathogenic → lead from `alphamissense` filtered → map to genomic → enrich). Harder when
     scores are pure refinements on a non-score lead (then either keep a thin SQL score-join just for the
     filter, or over-fetch + filter in app). This is the real architectural decision.
4. **Reconcile `ids[]` (MappingRepo CTE) and filter-browse (GenomicVariantRepo)** — both generate+annotate in
   SQL; `ids[]` is just "identifier is the lead". One builder with pluggable lead.
5. **Driver model** (`MappingRequestValidator`) — the static driver/refinement split is the only place the
   "DB-space selectivity" leaks into user-facing validation; see advanced_search_query_design.md §4b. Could
   become a selectivity-ranked lead-picker (parked).

## 5b. Index coverage — NOT the bottleneck (checked 2026-06-14)

The slim mapping's indexes = the fat set minus the four column-groups that moved to dims, each re-indexed on
its new home: `(ensg…)/(ensp…)`→`ensembl_transcript`(ensg/ensp/enst)+`ensembl_gene`(ensg PK);
`gene_name`→`protein(gene_name)`; `(is_canonical,accession)`→`protein(accession PK)`+filter. Every access
pattern from §3 is covered (genomic lead, protein lead, transcript/gene join, canonical, gene_name). So the
measured slowdowns are **planner behaviour on chained dim joins** (a `rows=1` estimate → seq-scan of
`ensembl_gene` *despite its PK*; lost `codon_table` Memoize), **not** missing indexes — they are not tunable
with indexes. This is structural, hence the rethink.

## 5c. Future requirement (design driver): apply the SAME filters to USER INPUTS, not just browse

Today filters are browse-only. The goal is to filter user-input results too (e.g. upload 10k variants →
show only AM-pathogenic / known / in-a-pocket). This **decides** the §6 lever:

- Filters baked into browse SQL are **structurally unreusable on inputs** (inputs are already-resolved
  variants, not generated from a lead) → the SQL-mega-join model is a dead end for this goal.
- The only model that supports it: **filtering is a predicate over RESOLVED + ENRICHED variants**, and that
  enriched set is produced identically for input and browse. ⇒ commit to candidate #3.

**Unifying design (put the seams in now, even if input-filtering ships later):**
```
source (USER input | browse lead)
  → resolve candidate variants (chr/pos/ref/alt, accession/protein_pos, codon/strand)
  → enrich once (consequence via Codon; scores batched; dim via cache; annotations lazy)
  → FILTER  (source-agnostic predicate over the enriched variant)   ← shared, reusable
  → paginate
```
Primitives:
1. **Enriched-variant model carrying every filterable attribute** (known, consequence, CADD/AM/popEVE/ESM/
   conservation, feature flags, disease, allele-freq, canonical) — one model the filter reads. Today these
   are split between browse-SQL joins and the input path's attached scores; unify them.
2. **Filter = `test(enrichedVariant)->bool`**, identical for input- and browse-sourced variants. This is what
   delivers filters-on-input for free.
3. **Filter optionally pushable as a lead/bound** — browse pushes the most-selective filter to SQL to bound
   generation, applies the rest as predicates; input is self-bounded, so all filters are predicates (with
   optional pushdown into the batch score-fetch).

**Cost to plan for:** filtering forces **paginate AFTER enrich+filter** (input today paginates the raw list
first, enriches one page). So input-filtering must enrich beyond one page → ties directly into the queued
result-set streaming / memory work. Same shape browse already needs.

## 6. Open question for the redesign — RESOLVED by §5c

The whole thing hinges on one decision: **does filter-browse keep a SQL path that filters/sorts by score
(needing the SQL consequence + the dim joins), or does it become "lead → generate candidates → Java enrich"
like the input path?** Candidate #3 is the big lever; everything else (codon unification, alt-allele
unification, dim-from-cache) is compatible with either answer.
