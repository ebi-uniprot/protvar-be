# ProtVar BE — Architecture & Design Decisions

## Search model

`MappingRequest` carries up to three mutually-exclusive identifying fields plus a set of
optional filters. The dispatcher in `MappingService.get()` picks one path:

| Mode | Request field | Handler | Returns |
|---|---|---|---|
| Variant query | `q` | `VariantParser` → `InputMapper` | exact total |
| Uploaded result | `resultId` | `ResultCacheHandler` | exact total |
| Identifier browse | `ids` | `IdentifierBrowseHandler` → `MappingRepo` | exact total |
| Filter-only browse | _(none of the above)_ | `GenomicVariantRepo` (4-strategy optimiser) | exact / capped / unknown |

`MappingRequestValidator` enforces a "driver" rule on every request: at least one of `q`,
`resultId`, `ids[]`, `pocket=true`, `interact=true`, `experimentalModel=true`, or
`known=true` must be set. Other filters (CADD, AM, popEVE, ESM1b, stability, allele
frequency, conservation) refine but cannot stand alone — their underlying tables are
14M–500M rows and would force an unbounded scan of the 169M-row mapping table.

### Identifier types (`IdentifierType`)

Browse queries via `ids[]` accept biological identifiers: `UNIPROT`, `GENE`, `PDB`,
`ENSEMBL`, `REFSEQ`. Variant strings and uploaded result IDs are handled separately via
`q` and `resultId` respectively — they are **not** identifier types.

### Filter-only browse and query optimisation

`GenomicVariantRepo` selects one of four strategies based on which filters are active, to
avoid unbounded full-table scans (~169M rows in the mapping table):

1. **Identifier-anchored** — identifier CTE UNION branches (currently unreachable; kept
   for future routing where identifier+filter combos benefit from this shape)
2. **Feature-anchored** — pocket / interaction / experimental-model tables (≤547K rows)
   as the leading join. Triggered by `pocket=true`, `interact=true`, or
   `experimentalModel=true`
3. **dbSNP pre-join** — `mapping_dbsnp_lookup` (~15M rows) as the leading table when
   `known=true` and no feature filter is set
4. **Score-anchored** — `gnomad_allele_freq` (52M) or `conserv_score` (14M) as the
   leading filter

Every strategy filters mapping rows on `m.is_canonical = true` so the response is one
canonical row per genomic variant. Alt isoforms are fetched on demand by the FE chevron
via `GET /mapping?q=<chr>-<pos>-<ref>-<alt>`.

`MappingRepo` handles identifier browse (`ids[]`) and is kept intact as a rollback target
for the filter-only path. Reverting filter-only is one line in `MappingService`: swap
`genomicVariantRepo.get()` back to `identifierBrowseHandler.pagedInput()`.

### Counting modes

`PagedMappingResponse.totalItems` and `totalCap` together encode three states:

| State | totalItems | totalCap | Meaning |
|---|---|---|---|
| Exact | N (≥0) | null | Identifier / variant / uploaded result. Always exact. |
| Capped | totalCap+1 | totalCap (e.g. 10000) | Filter-only with > totalCap matches. Display as "10,000+". |
| Unknown | -1 | totalCap | Filter-only where the bounded COUNT exceeded its 3 s timeout. Sparse multi-filter intersections (e.g. `pocket=true + known=true`) hit this. Display as "Many results". |

Pagination clamps at `floor(totalCap / pageSize)` when capped; uses `data.last` for
hasNext when unknown.

## Semantic search

Semantic search is a separate pipeline:

- It accepts a free-text query, runs vector similarity against pre-computed function
  embeddings, and returns a ranked list of `(accession, position)` hits.
- These hits feed *into* the standard mapping flow as identifier browse inputs — they are
  not a new kind of identifier type.
- The semantic search controller exposes its own endpoint; `MappingController` is not
  involved until the user navigates from a semantic result to a specific variant or
  position.

Adding a `SEMANTIC` or `SEMANTIC_RESULT` identifier type would mix two different
abstraction levels (query method vs. identifier type) and should be avoided.

## Future direction — unified `searchTerms` model (not adopted)

A consolidated input shape was considered: replace `q`, `resultId`, and `ids[]` with a
single `searchTerms: List<SearchTerm>` where each term carries a `value` and a `type`
from a unified `SearchType` enum (`VARIANT`, `INPUT_ID`, `UNIPROT`, `GENE`, `ENSEMBL`,
`PDB`, `REFSEQ`).

We deliberately stuck with the explicit three-field model because:

- The 3-field model is mutually exclusive at dispatch time and clear in OpenAPI/Swagger.
- A `searchTerms` shape would require a type discriminator on every term and
  validator-level enforcement of mutual exclusivity (VARIANT must be alone, INPUT_ID
  must be alone) which the type system gives us for free today.
- Auto-detection of term type adds parsing complexity for no obvious user-facing benefit.

If we later add a generic `/api/search` endpoint or need a polymorphic input for SDK
ergonomics, this is the direction to revisit.
