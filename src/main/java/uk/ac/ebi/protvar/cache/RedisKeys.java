package uk.ac.ebi.protvar.cache;

/**
 * Everything the BE stores in Redis. Every key is prefixed with cache.version (e.g. "v1"),
 * so bumping it moves to a fresh namespace:
 * - Spring caches ({@link Cache}): "v1::{cache}::{key}" (RedisConfig)
 * - keys written by hand ({@link Download}, {@link Report}): "v1:{key}" (see {@link #key})
 */
public final class RedisKeys {

    private RedisKeys() {}

    /** Prefixes a hand-written key with the cache version: "v1:{key}". */
    public static String key(String cacheVersion, String key) {
        return cacheVersion + ":" + key;
    }

    /** Spring caches (@Cacheable). No TTL unless noted; bounded by Redis maxmemory + LRU. */
    public static final class Cache {
        private Cache() {}
        // protein data: TTL app.retention.protein-cache (30d). Were FUN_HEADER and STR until 2026-11.
        public static final String UNIPROT_ENTRY_BY_ACCESSION = "uniprotEntryByAccession"; // accession -> UniProt entry (no features)
        public static final String STRUCTURES_BY_ACCESSION = "structuresByAccession";     // accession -> PDB structures
        public static final String SCORES_BY_ACCESSION = "scoresByAccession";            // accession -> scores
        public static final String POCKETS_BY_ACCESSION = "pocketsByAccession";          // accession -> pockets
        public static final String INTERACTIONS_BY_ACCESSION = "interactionsByAccession"; // accession -> interactions
        // uploads: TTL app.retention.submissions (90d)
        public static final String INPUTS = "inputs";                                    // upload id -> inputs
        public static final String INPUT_BUILDS = "inputBuilds";                         // upload id -> detected build
        public static final String INPUT_SUMMARIES = "inputSummaries";                   // upload id -> input summary
    }

    /** Download jobs (DownloadStatusService, DownloadGuard). */
    public static final class Download {
        private Download() {}
        public static final String ROOT = "download:";
        public static final String STATUS = ROOT + "status:";   // + job id -> DownloadStatus; TTL app.retention.downloads (30d)
        public static final String COUNTS = ROOT + "counts:";   // + {name}:total (no TTL) | {name}:by-day:{yyyy-mm-dd} (TTL 400d) -> count
        public static final String RATE = ROOT + "rate:";       // + client ip -> submissions this hour; TTL 1h
    }

    /** Dev notifications and weekly report (DevReportService). */
    public static final class Report {
        private Report() {}
        public static final String ROOT = "report:";
        public static final String SENT = ROOT + "sent:";       // + category -> urgent email sent this hour; TTL 1h
        public static final String ERRORS = ROOT + "errors:";   // + logger -> ERROR lines since last report; cleared on send
        public static final String RECENT = ROOT + "recent";    // list of latest ERROR lines (max 50); cleared on send
        public static final String LOCK = ROOT + "lock";        // one pod sends the report; TTL 1h
    }
}
