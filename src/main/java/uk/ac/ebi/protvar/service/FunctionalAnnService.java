package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.api.ProteinsAPI;
import uk.ac.ebi.protvar.converter.FunctionalInfoConverter;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.repo.FunctionalAnnRepo;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.uniprot.domain.features.Feature;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cache evolved from Map<String, DataServiceProtein> cache = new ConcurrentHashMap<>()
 * to Guava cache (offered automatic eviction when cache reaches specified max)
 * Cache<String, DataServiceProtein> cache = CacheBuilder.newBuilder().build()
 * then to disk-based MapDB, and finally to Spring Redis cache (through redisTemplate,
 * now cacheManager and Cacheable annotation).
 */
@Service
@RequiredArgsConstructor
public class FunctionalAnnService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionalAnnService.class);
    private static final String CACHE_NAME = "FUN";
    private final FunctionalAnnRepo functionalAnnRepo;
    private final ProteinsAPI proteinsAPI;
    private final CacheManager cacheManager;
    private final FunctionalInfoConverter converter;

    /**
     * Cache lookup distinguishes three cases:
     * <ul>
     *   <li><b>Cache hit with value</b>: a valid result is present.</li>
     *   <li><b>Cache hit with null</b>: previously looked up, result was not found (explicit null cached).</li>
     *   <li><b>Cache miss</b>: key is not present in the cache (not yet checked).</li>
     * </ul>
     */


    /**
     * Preloads the cache with FunctionalInfo entries for the given accessions.
     * Attempts to retrieve data from the local DB first, and falls back to the external API if not found.
     * Caches both successful and unsuccessful lookups to avoid repeated fetching.
     *
     * @param accessions List of UniProt accessions to preload
     */
    public void preloadFunctionCache(Collection<String> accessions) {
        if (accessions == null || accessions.isEmpty()) return;

        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return;

        // Step 1: Check cache
        List<String> toFetch = accessions.stream()
                .filter(acc -> cache.get(acc) == null) // valueWrapper == null
                .toList();

        if (toFetch.isEmpty()) return;

        LOGGER.info("Preloading {} accessions: checking DB first", toFetch.size());

        // Step 2: Fetch from DB
        Map<String, UPEntry> dbEntries = functionalAnnRepo.getEntries(toFetch);
        List<String> stillMissing = new ArrayList<>();
        int dbHits = 0;

        for (String acc : toFetch) {
            UPEntry entry = dbEntries.get(acc);
            if (entry == null) {
                // Cache as "not found" to avoid future lookup
                stillMissing.add(acc);
                //cache.put(acc, null); // done in API fetch step
            } else {
                cache.put(acc, converter.convert(entry));
                dbHits++;
            }
        }

        int apiHits = 0;
        int notFound = 0;

        // Step 3: Fetch from API, 100 accessions at a time
        if (!stillMissing.isEmpty()) {
            List<Collection<String>> partitions = FetcherUtils.partition(stillMissing, ProteinsAPI.PARTITION_SIZE);

            for (Collection<String> part : partitions) {
                UPEntry[] apiEntries = proteinsAPI.getProtein(part);
                Map<String, UPEntry> apiFetched = apiEntries != null
                        ? Arrays.stream(apiEntries).collect(Collectors.toMap(UPEntry::getAccession, Function.identity()))
                        : Collections.emptyMap();
                for (String acc : part) {
                    UPEntry entry = apiFetched.get(acc);
                    if (entry == null) {
                        notFound++;
                        cache.put(acc, null);
                    } else {
                        cache.put(acc, converter.convert(entry));
                        apiHits++;
                    }
                }
            }
        }
        int total = accessions.size();
        int fetched = toFetch.size();
        LOGGER.info("Functional info preload: {} total, {} fetched ({} DB, {} API), {} already cached, {} not found",
                total, fetched, dbHits, apiHits, total - fetched, notFound);
    }

    /**
     * Returns the FunctionalInfo for the given accession, retrieving from cache if available.
     * Falls back to DB and API if not present in cache. Null indicates not found.
     */
    public FunctionalInfo get(String accession) {
        if (accession == null || accession.isEmpty()) return null;

        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return null;

        // Check cache first
        Cache.ValueWrapper wrapper = cache.get(accession);
        if (wrapper != null) {
            return (FunctionalInfo) wrapper.get(); // may be null (cached not found) // <-- Redis: deserialized object
        }

        // Not in cache → try DB
        UPEntry entry = functionalAnnRepo.getEntry(accession);

        if (entry != null) {
            FunctionalInfo info = converter.convert(entry); // <-- new Java object
            cache.put(accession, info);
            LOGGER.info("{} Functional info loaded from database", accession);
            return info;
        }

        // Not in DB → try API
        UPEntry[] apiResults = proteinsAPI.getProtein(List.of(accession));
        if (apiResults != null && apiResults.length > 0) {
            FunctionalInfo info = converter.convert(apiResults[0]); // <-- new Java object
            cache.put(accession, info);
            LOGGER.info("{} Functional info loaded from api", accession);
            return info;
        }

        // Not found → cache null
        cache.put(accession, null);
        LOGGER.info("{} Functional info not found", accession);
        return null;
    }

   /*
    Every time you do cache.get(key), the cached value is deserialized from Redis.
    FunctionalInfo fi1 = get("P12345", 25);  // deserialized from Redis
    FunctionalInfo fi2 = get("P12345", 100); // separate deserialization from Redis
    These should be independent objects.

    You don’t need to copy it before modifying things like .setPosition(...)
    You’re safe to filter the features list or modify other fields
     */
    public FunctionalInfo get(String accession, int position) {
        FunctionalInfo fromCache  = get(accession);

        if (fromCache != null) {
            fromCache.setPosition(position);

            List<Feature> features = fromCache.getFeatures();
            if (features == null || features.isEmpty()) {
                fromCache.setFeatures(Collections.emptyList());
                return fromCache;
            }

            List<Feature> filteredFeatures = features.stream()
                    .filter(f -> {
                        try {
                            int start = Integer.parseInt(f.getBegin());
                            int end = Integer.parseInt(f.getEnd());

                            // For DISULFID, check if position matches start OR end exactly
                            if ("DISULFID".equals(f.getType())) {
                                return position == start || position == end;
                            }

                            // For other features, check if position is within range
                            return position >= start && position <= end;
                        } catch (NumberFormatException | NullPointerException e) {
                            // Skip features with invalid or missing start/end
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            fromCache.setFeatures(filteredFeatures);
        }
        return fromCache;
    }
}
