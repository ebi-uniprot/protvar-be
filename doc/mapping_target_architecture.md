# Mapping & advanced-search — target architecture

> **STATUS: DEFERRED to a FUTURE round (decided 2026-06-14).** This whole design (one source-agnostic
> pipeline + filters-on-user-inputs) is parked. It is a high-value feature (many users want to filter their
> own uploaded variants), so it is recorded here in full — but the CURRENT round is scoped to making the
> *existing* functionality work end-to-end (FE-BE-DB) on the new remapped slim+dim output, NOT this redesign.
>
> **Storage decision for when we build it (hybrid):** keep the raw user input in **Redis** (as today);
> persist the *enriched/resolved candidate set* into a **TTL'd temp DB table**, lazily (re)created from the
> Redis raw input when accessed/expired. This makes filter+sort+paginate over 1M-row inputs possible (SQL
> over an indexed, sortable relation — Redis can't sort/index), without permanently storing every upload.
> Sort is the reason materialisation is required (you must score+filter+sort the whole set for page 1).

Concrete target design, derived from `mapping_current_state.md`. One source-agnostic pipeline that produces
the **same** `PagedMappingResponse` the FE already consumes, but with filtering as a reusable predicate over a
unified enriched-variant model — so the *same filters work on user inputs and on browse*, the SQL mega-join
and the two parallel machineries disappear, and the planner-fragile dim-join chain is gone.

> Status: DESIGN (target). Companion to mapping_current_state.md (as-is) and advanced_search_query_design.md
> (the query-level findings, now subsumed here). No code yet beyond the `Codon` consequence util (done).

---

## 0. The one idea (SCALE-CORRECTED — inputs can exceed 1,000,000 variants)

In-memory "enrich every candidate then filter" does **not** survive a 1M-row input — you cannot enrich +
hold + paginate a million variants per request. So filtering must be **indexed SQL over a candidate
relation**, made *clean* by **precomputing the consequence once** so the filter query needs no codon/strand/
gene joins:

```
source (USER input | browse LEAD)
  → resolve            coords + (accession, protein_pos, codon, strand)
  → consequence ONCE   Java Codon → store wt_aa, mt_aa, is_canonical on each candidate
  → candidate RELATION  chr,pos,ref,alt, accession, protein_pos, wt_aa, mt_aa, is_canonical
       • large input  → MATERIALISE once per resultId (cached, like the existing upload retention)
       • browse       → the lead-bounded set (inline CTE / temp)
  → FILTER = SQL       candidate ⋈ score/feature/freq tables  + WHERE + ORDER + LIMIT/OFFSET   ← scales, LIMIT-pushdown
  → display-enrich     PAGE ONLY: dim from cache, annotation URIs (Java)
  → assemble           PagedMappingResponse  (reuse GeneConverter/IsoformConverter)
```

Why this is clean (not the old mega-join): with `mt_aa`/`is_canonical` **stored** on the candidate, every
filter is a plain indexed join — `am ON (accession,position,wt_aa,mt_aa)`, `cadd ON (chr,pos,ref,alt)`,
`conserv ON (accession,protein_pos)` — with **no `rna_base_for_strand`, no `codon_table`, no
`ensembl_transcript/gene` joins** in the filter query. The fragile dim-join chain cannot occur, and SQL keeps
the filter+sort+paginate (with `LIMIT` pushdown) that scales to 1M.

The same `MappingRequest` filter set compiles to the same SQL over the candidate relation whether the
candidates came from a user upload or a browse lead → **filters-on-user-inputs falls out**, at scale.
Unfiltered large-input browse keeps today's paginate-the-raw-list-first (no materialisation).

---

## 1. Core types

### `VariantCandidate` (resolution output — coords only)
`chromosome, genomicPosition, refAllele, altAllele, accession, proteinPosition, codon, codonPosition, enst`.
Lightweight; one per (genomic variant × mapped accession). Produced by any source. No scores, no consequence.

### `EnrichedVariant` (the filter target — everything filterable in one place)
Candidate **plus**:
- `consequenceAA` / `consequenceType` — DERIVED via `Codon.altAA` (done).
- dim: `isCanonical, geneName, proteinName, ensp, ensg, isManeSelect, reverseStrand` — from caches.
- genomic scores: `cadd`; `known` (in dbsnp); `alleleFreq`.
- protein scores: `am, popEve, esm1b, conservation`.
- feature flags: `pocket, interact, experimentalModel, ptm, mutagen, domain, binding, actsite, transmem, disease, stability`.

This is the single model every filter reads. Today these attributes are scattered (browse-SQL joins +
input-path attachments); here they're attached once, uniformly.

> Note: a genomic variant maps to a gene/isoform tree; filter semantics = "variant passes if it has an
> isoform satisfying the predicate" (matches today's SQL-join inclusion). So `EnrichedVariant` carries its
> isoform set; protein-level attributes are per-isoform, evaluated with `anyMatch`.

### `VariantFilter` (the reusable predicate)
`Predicate<EnrichedVariant>` built from the `MappingRequest` filter fields. Composed (AND) from per-attribute
sub-filters. Each sub-filter also optionally exposes a **`LeadHint`** (a table + SQL constraint) so it can
*bound generation* when chosen as the browse lead (§3). Identical predicate runs for input- and browse-sourced
variants — this is what delivers "filters on user inputs" for free.

---

## 2. Sources (resolve → candidates)

| source | trigger | how it resolves candidates | replaces |
|---|---|---|---|
| **InputSource** | `q` / `resultId` / file | parse → `BuildProcessor`/`Id2Gen`/`Coding2Pro`/`Pro2Gen` → candidates (alt = user's, or derived from user AA, or all-3) | `InputMapper.preprocess` (kept, lightly adapted) |
| **LeadSource** | `ids[]` **or** any filter (browse) | pick the most-selective filter's `LeadHint` as the SQL lead; generate candidates (alts = all-3 / dbsnp `known_alts`) | **`GenomicVariantRepo` (all 4 strategies) + `MappingRepo.getGenomicVariantsForInput`** |

Both emit `Stream<VariantCandidate>`. `LeadSource` is **one** generator with a pluggable lead — identifier,
dbsnp, feature-positions, score-table, or genomic-region — not N hand-written strategies.

---

## 3. Lead selection (browse) — dissolves the driver/refinement split

The browse candidate set must be bounded by *something*. Instead of the static driver/refinement table, pick
the lead by **selectivity** at request time:
- Each set filter with a `LeadHint` is a lead candidate; choose the most selective (smallest expected lead
  set). Identifier/region/`known`/feature tables are highly selective; score tables (`am`, `conservation`,
  …) are valid leads too (e.g. lead from `alphamissense WHERE am_class=PATHOGENIC` → map to genomic via the
  mapping). The remaining filters apply as predicates.
- This **resolves §4b** (alleleFreq/conservation as drivers) and the parked taxonomy question: there is no
  taxonomy, only "which available lead is most selective". The single hard gate stays: **≥1 filter** (so a
  lead exists). `known=true` default satisfies it.
- Safety net unchanged: `LIMIT`/stream + capped COUNT + timeout for weak leads.

For **input**, the source *is* the bound — all filters apply as predicates (with optional pushdown into the
batch score-fetch for efficiency).

---

## 4. Enrichment (candidates → enriched, batched)

Generalise `loadCoreMappingAndScores`: collect the candidate set's keys and **batch-fetch every filterable
attribute** in a handful of `WHERE (key) IN (…)` queries, then attach in Java:
- consequence via `Codon.altAA` (codon+strand from the candidate / transcript cache);
- dim via the Round-B caches (`createMapping` enrichment, factored out);
- scores: extend the existing batch (`ScoreNewRepo.getMappingScores` already does AM/popEVE) to CADD,
  conservation, ESM; `known`/allele-freq via batched dbsnp/gnomad lookups;
- feature flags via batched feature-table lookups by (accession, position).

Two enrichment tiers, for cost:
- **filter-enrich** (over the whole candidate set): only the attributes any active filter needs.
- **display-enrich** (page only, after pagination): the lazy annotation URIs (already how the FE works).

No `ensembl_gene` SQL join anywhere (strand from cache, or the 1-hop transcript denorm only if a residual SQL
codon path is kept). The §1 dim-join landmine cannot occur.

---

## 5. Filter → paginate → assemble

- **filter**: `candidates.filter(variantFilter)` — one predicate, both sources.
- **paginate**: after filtering. Count = filtered size (exact when the candidate set is bounded — always true
  for input and for a selective browse lead); capped/streamed when the lead is weak (reuse the cap/timeout).
  ⚠️ This is the one real shift from today (input currently paginates the raw list *first*); it requires
  enriching beyond one page → ties to the queued result-set **streaming** work.
- **assemble**: reuse `GeneConverter`/`IsoformConverter` → identical `PagedMappingResponse`. **FE unchanged.**

---

## 6. What each current piece becomes

| today | target |
|---|---|
| `MappingService.getInputs` dispatch (4 branches) | `source = (input ? InputSource : LeadSource)`; one path after |
| `GenomicVariantRepo` (4 strategies, SQL mega-join, SQL codon, SQL score joins) | **deleted** → `LeadSource` (generate) + `VariantEnricher` (attach) + `VariantFilter` (predicate) |
| `MappingRepo.getGenomicVariantsForInput` (ids CTE) | **deleted** → `LeadSource` with identifier lead |
| `MappingRepo.createMapping` cache enrichment | factored into `VariantEnricher` (shared) |
| `InputMapper.loadCoreMappingAndScores` | generalised into `VariantEnricher.filterEnrich` |
| SQL `rna_base_for_strand` + `codon_table` join | gone from the live path (Java `Codon`); keep as optional `variant.codon.source=sql` only if ever needed |
| `MappingRequestValidator` driver/refinement list | selectivity-ranked lead-picker + the ≥1-filter gate |
| `GeneConverter`/`IsoformConverter` / `PagedMappingResponse` | unchanged (response contract preserved) |

---

## 7. Requirements check

- **Filters on user inputs** (the driver): the same `VariantFilter` runs on `InputSource` output → free.
- **No planner landmine**: no chained dim joins; SQL only resolves a lead.
- **Simpler**: one pipeline, one enrichment, one filter set, one consequence routine; two repos + four
  strategies + duplicated codon collapse.
- **Same FE**: identical response DTO + lazy annotation URIs + canonical-lead/lazy-isoform behaviour.
- **Canonical handling** ([[project_canonical_handling_cutover]]): isoform set lives on `EnrichedVariant`;
  lead-row + lazy `/isoforms` is an assembler/endpoint concern, orthogonal.

## 8. Migration (incremental, each step parity-checked vs current output)

1. ✅ `Codon` consequence util (+ `Pro2Gen` migrated).
2. Define `VariantCandidate` / `EnrichedVariant`; factor `createMapping` enrichment into `VariantEnricher`.
3. Extend `VariantEnricher` to batch ALL filterable attributes (CADD/conserv/ESM/feature-flags/known/freq).
4. Implement `VariantFilter` predicates (one per request filter).
5. Implement `LeadSource` + selectivity lead-picker; route **filter-only browse** through
   resolve→enrich→filter→paginate; keep `GenomicVariantRepo` behind a flag until parity verified (EXPLAIN +
   row-for-row diff on representative filter combos).
6. Route **`ids[]`** through `LeadSource` (identifier lead); retire `MappingRepo.getGenomicVariantsForInput`.
7. **Apply `VariantFilter` to the input path** → ship filters-on-user-inputs (needs streaming, step ties to
   [[project_download_streaming_refactor]]).
8. Delete `GenomicVariantRepo` + dead SQL; finalise the lead-picker; remove the SQL codon unless kept by flag.

## 9. Open risks to validate during build

- **Weak-lead browse** (only loosely-selective filters set): candidate set large → enrich cost. Mitigation:
  lead-picker prefers the most selective; cap/timeout/stream; consider requiring a reasonably selective filter.
- **Score-as-lead reverse codon**: leading from a score table needs "which alt yields this mt_aa" (Java
  reverse of `Codon.altAA`) — straightforward but must be covered by tests.
- **Pagination-after-filter memory** for large inputs/weak leads — the streaming work is a hard dependency
  for filters-on-input at scale.
- **Count semantics**: exact for bounded, capped/unknown for weak leads — keep the FE's existing
  exact/capped/unknown handling.
- **The two browse "driver" tables are reductions of full-genome data, with INCONSISTENT bases**
  (see `[[project_browse_driver_tables]]` / memory). `known` leads from **`mapping_dbsnp_lookup`** =
  dbSNP (~1B+ raw) ∩ **mapping coordinates** (~15M). CADD leads/enriches from **`coding_cadd`** = CADD
  (full-genome) restricted by **exon/protein boundaries** (`cadd.exon.boundaries`). These ARE the lead/score
  sources this design depends on, so the redesign must treat them coherently:
  reconcile the two reduction bases (mapping-coords vs exon/protein boundaries → one definition of "served
  coding positions"?), recap the CADD exon-vs-protein-coords ambiguity, and review the full dbSNP import. All
  derive from the mapping → rebuild for 2026_02 (`coding_cadd`/`uniprot_refseq` = importer steps;
  `mapping_dbsnp_lookup` = BE-side SQL, can source from the `_enriched` view). Decide direction before rebuild.
