# Advanced search / variant browse — query-layer redesign (FE-first)

Design note for the "3b" step of the slim-mapping cut-over: collapse the ad-hoc genomic-variant query
strategies into one builder that works against the **slim** `rel_*_genomic_protein_mapping` + dim tables,
without regressing query plans. Written FE-first: the filter panel and its defaults *are* the requirements.

> Status: DESIGN — not yet implemented. The perf claims in §4 are benchmark-backed (see
> [[project_bulk_query_cache_enrich]] / §4). Implementation order in §8.

---

## 1. The user-facing contract (what the FE actually sends)

Filter panel: `protvar-fe SearchFilters.tsx`; request built in `ResultPage.tsx` (~228-260). Groups:

- **Variant Type** (radio): **"Known variants" (DEFAULT)** vs "Potential variants".
  Wire: `known=true` (default) / `known=undefined` (potential). `defaultFilters.ts: variant:'known'`.
- **Functional**: PTM, Mutagenesis, Domain, Binding, Active Site (checkboxes) → `ptm/mutagen/domain/binding/actsite`; Conservation slider 0–1 → `conservationMin/Max`.
- **Population**: Disease Association → `diseaseAssociation`; Allele Frequency (Very Rare/Rare/Low/Common) → `alleleFreq[]`.
- **Structural**: Transmembrane, Experimental Model, PP Interface, Predicted Pocket → `transmem/experimentalModel/interact/pocket`; Stability → `stability[]`.
- **Consequence**: CADD, AlphaMissense, popEVE (multi-select) → `cadd[]/am[]/popeve[]`; ESM-1b slider → `esm1bMin/Max`.
- **Identifiers / position**: `ids[]` (UniProt/Gene/PDB/Ensembl/RefSeq), `startPos/endPos` (single-accession only).
- **Sort**: CADD / AlphaMissense / popEVE / ESM1b, asc|desc → `sort/order`.

Default initial state = `known:true`, everything else empty.

Result UI (`ResultTable.tsx`, `PaginationRow.tsx`): paginated; total is **exact**, **capped** ("10,000+",
"Showing the first 10,000 results — refine filters to see more") or **unknown** (`-1`, "End of available
results"). Each variant renders a **lead row** (canonical, "can") with a chevron that **lazy-loads alternate
isoforms** via a single-variant fetch ("No other isoforms." when none). Results group by genomic variant + gene.

---

## 2. Reframe: there is no user "primary filter"

From the user's side every filter is equal — they tick any combination. "Primary / driving / lead filter"
is **purely an internal query-planning choice**: which table to put in the leading `FROM` so the search
space is bounded before the expensive joins. So the question "do we still need the primary filters?" resolves to:

- **No** — we drop the curated, hand-coded strategy list (the current 4 `GenomicVariantRepo` strategies +
  the separate `MappingRepo` identifier path). They are ad-hoc and duplicated.
- **Yes** — we keep the *concept* as a **lead-selection policy** (§3): derive the lead from whichever
  applied filter is most selective. Everything else is a refinement predicate.

---

## 3. Lead-selection policy (replaces the ad-hoc strategies)

Pick the lead in priority order (most selective first); apply all other set filters as secondary
joins/`WHERE` after the lead:

1. **`ids[]` / single-accession `startPos..endPos`** → lead from the identifier/position set (tiny).
2. **`known=true`** (the default) → lead from `mapping_dbsnp_lookup` (~15M precomputed `(chr,pos,ref)→known_alts`); alts come from `unnest(known_alts)`.
3. **A structural-feature driver** set (`pocket/interact/experimentalModel`) → lead from the feature-positions CTE (small tables). Alts via `VALUES('A','T','G','C') ON alt<>ref`. (`alleleFreq`/`conservation` are refinement-only under the current/kept policy — they refine, they don't lead; see §4b.)
4. **`known=false` (Potential)** → always accompanied by ≥1 other filter (enforced, §4), so the lead comes
   from that filter (the identifier/position/annotation table); alts via `VALUES`. The no-lead case is
   disallowed, so there is always a selective lead.

**Refinement-only filters** (never a lead): `cadd/am/popeve/esm1b`, `diseaseAssociation`, `ptm/mutagen/domain/binding/actsite/transmem`, `stability`. These join the score/feature tables after the lead and filter/sort.

(Ordering note: keep today's rule that a lead with a usable order index supports default sort + `LIMIT`
pushdown; dbsnp/allele-freq leads only allow user-requested score sorts to avoid materialisation.)

---

## 4. The "Known / Potential" default and the all-potential path  *(the open question)*

- **Known (default)** → leads from `mapping_dbsnp_lookup` → bounded, fast.
- **Potential + some filter** → that filter leads; alts via `VALUES`.
- **Potential + NO other filter** → **DISALLOWED (DECISION 2026-06-13).** Selecting "Potential" requires at
  least one accompanying constraint. Rationale: the unfiltered potential space is ~10⁸–10⁹ rows (un-browseable
  and the heaviest query the system can serve), and every meaningful "potential variants" question already
  carries a scope. The `known=true` default means a user who never touches the panel always satisfies this.
  - **"Accompanying filter" = a DRIVER, not any refinement.** Already encoded in
    `MappingRequestValidator.hasDriver()`: drivers = `q`, `resultId`, **`ids[]`**, `pocket`, `interact`,
    `experimentalModel`, `known`. Refinements that **cannot** stand alone = `cadd`, `am`, `popeve`, `esm1b`,
    `stability`, `alleleFreq`, `conservation`, `startPos/endPos`, `sort` (their tables are 14M–500M rows → no
    selective lead). The driver set is precisely "filters whose lead table is small" (pocket ~547K, interact
    ~68K, expModel ~203K, dbsnp_lookup ~15M) — the non-arbitrary answer to "why these primary filters".
  - **Already enforced**: `hasDriver()` requires ≥1 driver for *every* request, and `known` is a driver — so
    "deselect Known → need another driver" is the live behaviour. Gap is FE-side: surface the
    `NO_DRIVER_MESSAGE` inline + disable submit (it already names "provide an identifier or … Pocket /
    Interaction / Experimental Model / Known").
  - **OPEN (decision needed)**: `alleleFreq` + `conservation` are refinement-only here, yet
    `GenomicVariantRepo` has lead strategies 4a/4b for them (possibly currently unreachable standalone).
    Decide: (a) keep refinement-only, or (b) promote to drivers so "Potential + Common freq / high
    conservation" is allowed. Affects whether strategies 4a/4b survive the unification.
  - **Architectural payoff**: with ≥1 filter always present, **there is always a selective lead table** — the
    no-lead full-mapping scan is eliminated. The capped-COUNT + 3s-timeout + FE "first 10,000 / End of
    available results" become a **safety net**, not a primary path. (A full-genome potential-variant *dump*,
    if ever required, is a separate streaming-download concern, not interactive browse.)

**Benchmark (same 2026_02 data, fat inline schema vs slim; bounded realistic scopes):**

| strategy | rows | fat (current) | slim, dims SQL-joined | **slim + cache-enrich** |
|---|---|---|---|---|
| gnomad | 7,241 | 47 ms | 141 ms | **38 ms** |
| conserv | 25,860 | 138 ms | 228 ms | **18 ms** |
| dbsnp | 29,372 | 131 ms | **23,777 ms** ⚠️ | **221 ms** |
| function-feature | 190,548 | 48 ms | **4,800 ms** ⚠️ | **41 ms** |
| gene-name | 71,676 | 1,581 ms | 272 ms | **9 ms** |

The ⚠️ rows are the landmine: SQL-joining `ensembl_transcript`→`ensembl_gene` makes the dim chain estimate
collapse to `rows=1`, so the planner seq-scans `ensembl_gene` once-per-output-row (100–180× slowdown).
`ANALYZE` does not fix it. → **never SQL-join transcript/gene in the bulk path.**

---

## 5. The validated query shape

```
lead (most-selective applied filter, §3)
  → JOIN slim rel_*_genomic_protein_mapping m            -- slim cols only
  → JOIN rel_*_protein p ON p.accession=m.accession AND p.is_canonical   -- ONLY dim that stays in SQL: carries the is_canonical filter; cheap PK/hash join
  → alt expansion (VALUES, or unnest(known_alts) for the dbsnp lead)
  → refinement joins (score/feature tables) + WHERE + ORDER
  → LIMIT/OFFSET
```

Then **enrich in Java from the startup caches** (same as the point path, `MappingRepo.createMapping`):
`reverse_strand` ← EnsemblGeneCache(ensg); `ensp`/`ensg(+v)`/`is_mane_select` ← EnsemblTranscriptCache(accession,enst);
`gene_name`/`protein_name` ← ProteinCache(accession). `gene_name` *filter* leads from the small `protein` dim.

---

## 6. Decision needed: where the alt-codon / consequence is computed

Today the alternate codon → consequence AA is computed **in SQL** in *both* paths
(`MappingRepo:389-401`, and every `GenomicVariantRepo` strategy):
`rna_base_for_strand(alt, m.reverse_strand)` spliced into `m.codon` + `LEFT JOIN codon_table`.
This needs `reverse_strand` **on the mapping row** — which the slim schema removes.

**REVISED (2026-06-14): the consequence AA is a SQL JOIN KEY for the bulk path, not output-only.**
`GenomicVariantRepo` joins the predictor tables on the variant's resulting AA: `am.mt_aa = c.amino_acid`,
`popeve.mt_aa = c.amino_acid`, `esm.mt_aa = c.amino_acid`, `foldx.mutated_type = c.amino_acid` (and the
wild-type side `am.wt_aa = m.protein_seq` → slim `amino_acid`). So filtering/sorting by AlphaMissense /
popEVE / ESM / stability *requires* the alt-AA in SQL. Therefore:
- **Bulk path: keep the SQL codon→AA** (`rna_base_for_strand` + `codon_table`) — it's load-bearing, not a
  fallback. This needs `reverse_strand` in the row: it lives on the **slim mapping fact** (the `_enriched`
  view sources `m.reverse_strand` from the mapping), **NOT** on `ensembl_transcript`. See the perf note below.
- **Point path: Java AA** (`Codon.altAA`, added 2026-06-14, unit-tested + DB-parity-checked) where the AA is
  output-only (`MappingRepo.createMapping`).
- **Perf — RESOLVED (2026-06-17): `reverse_strand` on the mapping fact, not the transcript.** Strand-on-transcript
  made the codon expr depend on the transcript join, so the planner deferred consequence/`mt_aa`/score matching to
  late Join Filters over a huge intermediate → **~40× slower** on consequence-AA-keyed filter browses (gnomad+AM,
  chr21 43.0–43.2M: 2915ms). `reverse_strand` is intrinsic to per-row codon math, so it belongs on the slim
  mapping fact (1 bool × 266M); the view exposes it as `m.reverse_strand` and the codon resolves early again →
  **171ms** (vs fat 73ms). The earlier "~4× lost `codon_table` Memoize" was a **misdiagnosis** — forcing the
  Memoize doesn't change the time; the strand-on-transcript blowup was the real cause. (importer: strand on the
  mapping fact; be: `create_mapping_enriched_view.sql` sources strand from `m`.)

--- superseded ---
**DECISION (2026-06-13): Java active, SQL preserved as a live, switchable fallback.**

- **Active path = Java.** Compute strand + alt-codon + consequence in Java (EnsemblGeneCache strand + a
  codon translator), shared by the bulk and point paths. Robust — no transcript/gene SQL joins, immune to
  the §4 planner landmine. Safe because the AA consequence is output-only (filters/sorts are on scores, not
  the AA). This is the default.
- **SQL preserved.** The SQL codon approach (the `rna_base_for_strand` DB function + the codon-splice
  expression + `codon_table` join) is **kept and switchable** via a flag (e.g. `variant.codon.source=java|sql`,
  default `java`) — retained both for history and as a real fallback if we want to push the math back into
  the DB. It's a clean bit of SQL; we don't throw it away.
- **Dependency that makes the SQL fallback *work*:** the SQL branch needs `reverse_strand` in the row, so we
  also **denormalise `reverse_strand` onto `rel_*_ensembl_transcript`** (all transcripts of a gene share
  strand). One cheap `enst`-index join then supplies strand to the SQL branch (no 2-hop gene join). Without
  this the SQL path would be preserved-but-broken; with it, `variant.codon.source=sql` actually runs.

So: ship A (Java default) **and** keep the strand-on-transcript denorm so the retained SQL path stays live.

---

## 7. Reconcile GenomicVariantRepo vs MappingRepo

Both are live: `GenomicVariantRepo.get()` = filter-only browse (4 strategies); `MappingRepo.getGenomicVariantsForInput()`
= identifier path (one CTE). They overlap heavily (alt expansion, codon math, score joins). **Target: one
builder** serving both — identifiers are just lead #1 in §3 — sharing the §5 enrichment + §6 codon routine
with the point path. Removes the duplication the prior review flagged.

---

## 8. Implementation order

1. Extract a shared **enrichment routine** (§5) + codon routine (§6 decision) from `MappingRepo.createMapping`; unit-test its output against the current SQL on a sample.
2. Implement the **lead-selection policy** (§3) + **one builder** (slim mapping + `protein` join).
3. Port the refinement filters (scores/features) as secondary joins.
4. Preserve the **capped/timeout COUNT** + ordered `LIMIT` scan for the all-potential path (§4).
5. **EXPLAIN every realistic FE filter combination** on `protvarwrite` (real annotation tables) before/after.
6. Keep the lead-row + lazy `/isoforms` behaviour the FE already expects (§9).
7. Deploy BE + time the Delphix `main` refresh together (prod→main rule).

Also: have the importer run `ANALYZE` on the dims post-load (autoanalyze ran, but make it explicit in IndexingStage).

## 9. Canonical handling alignment

The FE already renders a canonical **lead row** + lazy alternate isoforms (chevron → single-variant fetch).
The browse returns canonical lead rows (the `protein.is_canonical` join); a dedicated `/isoforms` endpoint
serves the per-variant expansion. This is forward-compatible with the canonical-handling cut-over
([[project_canonical_handling_cutover]]) — which later relaxes `is_canonical` from a hard filter to a
lead/ordering so non-canonical input still returns, led with a "a canonical exists" note.

## Open decisions

- ~~**§6**: Java vs SQL codon~~ — **RESOLVED 2026-06-13**: Java active + SQL preserved/switchable + strand denormalised onto `ensembl_transcript`.
- ~~**§4**: allow Potential + zero filters?~~ — **RESOLVED 2026-06-13**: Potential requires ≥1 accompanying *driver* (already enforced by `MappingRequestValidator.hasDriver()`; `ids[]` confirmed a driver). Eliminates the no-lead full scan.
- ~~**§4b**: promote `alleleFreq`/`conservation` to drivers?~~ — **DEFERRED 2026-06-13**: keep current driver/refinement set unchanged for the cut-over (`alleleFreq`/`conservation` stay refinement-only). The distinction works, is safe, and is **independent of the slim-mapping + cache-enrich + unified-builder work** — don't couple a query-semantics redesign into the schema cut-over.

### Parked (post-cut-over, optional): drop the driver/refinement taxonomy
The static driver/refinement split is a hand-maintained proxy for *selectivity*. A leaner model could replace it with: (1) an internal selectivity-ranked **lead-picker** (pick the most selective applied filter as the SQL lead — works for any filter), (2) a **universal safety net** (`LIMIT` + `statement_timeout` + capped COUNT) so broad queries degrade gracefully instead of being pre-rejected, (3) a single "≥1 filter set" gate. This dissolves §4b (filters just rank by selectivity). Trade-off: more heavy queries reach the DB (bounded by timeout) and it leans harder on `LIMIT`-pushdown. **Only consider once the cut-over is proven**; it lives in one class + the lead step, so it's a reversible, isolated change.
