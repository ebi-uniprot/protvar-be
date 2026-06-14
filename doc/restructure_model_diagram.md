# Restructured mapping model — diagram (post Round-B cut-over)

Visual map of the slim+dim schema and how the BE consumes it. Spans both repos: the importer
(`protvar-import`) produces the tables; the BE (`protvar-be`) reads them. Mermaid (renders in IntelliJ with
the Mermaid plugin).

## 1. DB schema — star around the slim mapping fact

```mermaid
erDiagram
    PROTEIN ||--o{ GENOMIC_PROTEIN_MAPPING : "accession"
    ENSEMBL_TRANSCRIPT ||--o{ GENOMIC_PROTEIN_MAPPING : "accession+enst"
    ENSEMBL_GENE ||--o{ ENSEMBL_TRANSCRIPT : "ensg"
    PROTEIN ||--|| PROTEIN_SEQUENCE : "accession"
    UNIPROT_ACCESSION ||--o| PROTEIN : "accession (mapped subset of known)"

    GENOMIC_PROTEIN_MAPPING {
        bigint id PK
        varchar chromosome
        int genomic_position
        char base_nucleotide "ref (was 'allele')"
        varchar codon
        int codon_position
        varchar accession FK
        int protein_position
        char amino_acid "ref AA (was 'protein_seq')"
        bool is_match
        varchar enst FK
        varchar enstv
        varchar ense
    }
    PROTEIN {
        varchar accession PK
        varchar protein_name
        varchar gene_name
        bool is_canonical
        int length
    }
    PROTEIN_SEQUENCE {
        varchar accession PK
        text sequence
    }
    ENSEMBL_GENE {
        varchar ensg PK
        varchar ensgv
        varchar chromosome
        bool reverse_strand "canonical home"
    }
    ENSEMBL_TRANSCRIPT {
        varchar accession PK
        varchar enst PK
        varchar ensg FK
        varchar enstv
        varchar ensp
        varchar enspv
        bool is_mane_select
        bool reverse_strand "DENORM from gene (1-hop for BE codon)"
    }
    UNIPROT_ACCESSION {
        varchar accession PK
        bool is_canonical "full known set (~169k)"
    }
```

The fat columns that used to repeat on every one of the 266M mapping rows (`is_canonical`, `gene_name`,
`protein_name`, `reverse_strand`, `ensg/ensp/…`) now live once on the dim tables.

## 2. Compatibility view (BE bulk bridge — temporary)

```mermaid
flowchart LR
    M[rel_*_genomic_protein_mapping<br/>slim fact] -->|JOIN accession| P[rel_*_protein]
    M -->|JOIN accession+enst| T[rel_*_ensembl_transcript]
    P & T --> V[["rel_*_genomic_protein_mapping_enriched<br/>(VIEW — legacy fat shape, 0 storage)<br/>allele, protein_seq, is_canonical,<br/>gene_name, reverse_strand, ensp…"]]
    V -.->|bulk queries run unchanged| BULK
```
No `ensembl_gene` join (2-hop landmine); `reverse_strand` read 1-hop from the transcript. Removed when the
deferred redesign rewrites the bulk queries to read slim+dims directly.

## 3. BE consumption — two read paths

```mermaid
flowchart TD
    subgraph startup["Startup (@EventListener ApplicationStartedEvent)"]
        P[(protein)] --> PC[ProteinCache]
        EG[(ensembl_gene)] --> EGC[EnsemblGeneCache]
        ET[(ensembl_transcript)] --> ETC[EnsemblTranscriptCache]
        UA[(uniprot_accession)] --> UAC[UniprotAccessionCache]
    end

    subgraph point["POINT path — q / resultId / ids → single mappings"]
        MR1["MappingRepo.getMappingsBy{ChrPos,AccPos}"] -->|SELECT slim| SM[(slim mapping)]
        MR1 --> CM["createMapping(rs)"]
        PC & EGC & ETC --> CM
        CM --> G2P[GenomeToProteinMapping]
    end

    subgraph bulk["BULK path — filter-only browse / ids browse"]
        GVR["GenomicVariantRepo (4 strategies)"] --> VW[(enriched VIEW)]
        MR2["MappingRepo.getGenomicVariantsForInput"] --> VW
        VW --> VI[VariantInput coords]
    end

    CODON["Codon.altAA / altCodon / rnaBaseForStrand<br/>(consequence AA, Java — parity-checked vs SQL)"]
    CM --> CODON
    G2P & VI --> ASM["GeneConverter / IsoformConverter"]
    ASM --> RESP["PagedMappingResponse<br/>Input → GenomicVariant → Gene → Isoform → Transcript"]
```

## 4. Browse lead/driver + score tables (context — see project_browse_driver_tables)

```mermaid
flowchart LR
    DBSNP[(dbsnp_b156 ~1B)] -->|∩ mapping coords| LK[(mapping_dbsnp_lookup ~15M)]
    LK -->|known=true lead| GVR2[GenomicVariantRepo]
    CADDRAW[(CADD full-genome)] -->|by exon/protein boundaries| CC[(rel_*_coding_cadd)]
    CC -->|CADD filter/sort| GVR2
    GAF[(gnomad_allele_freq)] & CONS[(conserv_score)] -->|allele-freq / conservation lead| GVR2
    AM[(alphamissense)] & POP[(popeve)] & ESM[(esm)] -->|score filter/sort, keyed by mt_aa| GVR2
```
⚠️ The two reduction bases (dbSNP by mapping-coords vs CADD by exon/protein boundaries) are inconsistent and
under review before rebuild — see `project_browse_driver_tables` (memory) and target_architecture.md §9.

## Legend
- **slim fact** = `rel_<rel>_genomic_protein_mapping` (one row per genomic-position × accession × transcript).
- **dim tables** = protein / protein_sequence / ensembl_gene / ensembl_transcript / uniprot_accession.
- **VIEW** = `_enriched` compatibility bridge (temporary).
- **caches** load the dims into memory at startup; the point path enriches from them, the bulk path uses the view.
