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
import uk.ac.ebi.protvar.model.score.ConservScore;
import uk.ac.ebi.protvar.model.score.EsmScore;
import uk.ac.ebi.protvar.model.score.EveScore;
import uk.ac.ebi.protvar.model.score.Score;
import uk.ac.ebi.protvar.repo.*;
import uk.ac.ebi.protvar.utils.Commons;
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

    private final PocketRepo pocketRepo;

    private final InteractionRepo interactionRepo;

    private final FoldxRepo foldxRepo;

    private final ScoreRepo scoreRepo;
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

        // Step 1: Filter accessions not in cache
        List<String> toFetch = accessions.stream()
                .filter(acc -> cache.get(acc) == null)
                .toList();

        if (toFetch.isEmpty()) return;

        LOGGER.info("Preloading {} accessions: checking DB first", toFetch.size());

        // Step 2: Try fetching from DB
        Map<String, UPEntry> dbEntries = functionalAnnRepo.getEntries(toFetch);
        List<String> stillMissing = new ArrayList<>();
        int dbHits = 0;

        for (String acc : toFetch) {
            UPEntry entry = dbEntries.get(acc);
            if (entry == null) {
                // Cache as "not found" to avoid future lookup
                stillMissing.add(acc);
                cache.put(acc, null);
            } else {
                cache.put(acc, converter.convert(entry));
                dbHits++;
            }
        }

        int apiHits = 0;
        int notFound = 0;

        // Step 3: Fetch remaining from API
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
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return null;

        FunctionalInfo info = null;
        String source = "cache";

        Cache.ValueWrapper wrapper = cache.get(accession);
        if (wrapper != null) {
            Object value = wrapper.get(); // null means previously looked up, not found
            info = (FunctionalInfo) value;
        } else { // Key doesn't exist in cache, needs to be fetched

            // Try DB first
            UPEntry entry = functionalAnnRepo.getEntry(accession);

            if (entry != null) {
                info = converter.convert(entry);
                source = "database";
            } else {
                // Fall back to API
                UPEntry[] apiResults = proteinsAPI.getProtein(List.of(accession));
                if (apiResults != null && apiResults.length > 0) {
                    info = converter.convert(apiResults[0]);
                    source = "api";
                } else {
                    source = "not found";
                }
            }
            // Cache including null to indicate not found
            cache.put(accession, info);
        }
        String message = "not found".equals(source)
                ? String.format("%s Functional info not found", accession)
                : String.format("%s Functional info loaded from %s", accession, source);

        LOGGER.info(message);
        return info;
    }

    public FunctionalInfo get(String accession, int position) {
        FunctionalInfo functionalInfo = get(accession);

        if (functionalInfo != null) {
            functionalInfo.setPosition(position);
            functionalInfo.setFeatures(filterByPosition(functionalInfo.getFeatures(), position));
        }

        return functionalInfo;
    }

    public FunctionalInfo get(String accession, int position, String variantAA) {
        FunctionalInfo functionalInfo = get(accession, position);
        if (functionalInfo != null) {
            // Add novel predictions
            functionalInfo.setPockets(pocketRepo.getPockets(accession, position));
            functionalInfo.setInteractions(interactionRepo.getInteractions(accession, position));
            functionalInfo.setFoldxs(foldxRepo.getFoldxs(accession, position, variantAA));

            List<Object[]> list = new ArrayList<>();
            list.add(new Object[] { accession, position });
            // Add other scores
            Map<String, List<Score>>  scoresMap = scoreRepo.getScores(list, true)
                    .stream().collect(Collectors.groupingBy(Score::getGroupBy));

            String keyConserv = Commons.joinWithDash("CONSERV", accession, position);
            String keyEve = Commons.joinWithDash("EVE", accession, position, variantAA);
            String keyEsm = Commons.joinWithDash("ESM", accession, position, variantAA);

            scoresMap.getOrDefault(keyConserv, Collections.emptyList()).stream().findFirst()
                    .map(s -> ((ConservScore) s).copy()).ifPresent(functionalInfo::setConservScore);

            scoresMap.getOrDefault(keyEve, Collections.emptyList()).stream().findFirst()
                    .map(s -> ((EveScore) s).copy()).ifPresent(functionalInfo::setEveScore);

            scoresMap.getOrDefault(keyEsm, Collections.emptyList()).stream().findFirst()
                    .map(s -> ((EsmScore) s).copy()).ifPresent(functionalInfo::setEsmScore);
        }
        return functionalInfo;
    }

    private List<Feature> filterByPosition(List<Feature> features, int position) {
        if (features == null) return Collections.emptyList();
        return features.stream()
                .filter(f -> {
                    try {
                        int start = Integer.parseInt(f.getBegin());
                        int end = Integer.parseInt(f.getEnd());
                        return position >= start && position <= end;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }).collect(Collectors.toList());
    }
}
