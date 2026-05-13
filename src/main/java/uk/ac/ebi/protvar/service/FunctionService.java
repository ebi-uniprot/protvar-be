package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.converter.FunctionalInfoConverter;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.repo.FunctionRepo;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.uniprot.domain.features.Feature;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FunctionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionService.class);
    private static final String CACHE_NAME = "FUN";
    private final FunctionRepo functionRepo;
    private final CacheManager cacheManager;
    private final FunctionalInfoConverter converter;

    /**
     * Preloads the FUN cache for the given accessions. Misses on the DB are cached as null
     * so the same lookup doesn't re-query.
     */
    public void preloadFunctionCache(Collection<String> accessions) {
        if (accessions == null || accessions.isEmpty()) return;

        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return;

        List<String> toFetch = accessions.stream()
                .filter(acc -> cache.get(acc) == null)
                .toList();
        if (toFetch.isEmpty()) return;

        Map<String, UPEntry> dbEntries = functionRepo.getEntries(toFetch);
        int dbHits = 0;
        int notFound = 0;
        for (String acc : toFetch) {
            UPEntry entry = dbEntries.get(acc);
            if (entry == null) {
                cache.put(acc, null);
                notFound++;
            } else {
                cache.put(acc, converter.convert(entry));
                dbHits++;
            }
        }
        LOGGER.info("Functional info preload: {} total, {} fetched ({} DB, {} not found), {} already cached",
                accessions.size(), toFetch.size(), dbHits, notFound, accessions.size() - toFetch.size());
    }

    /**
     * Returns the FunctionalInfo for the given accession, from cache when warm and otherwise
     * from the DB. Null indicates not found (and gets cached as null).
     */
    public FunctionalInfo get(String accession) {
        if (accession == null || accession.isEmpty()) return null;

        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return null;

        Cache.ValueWrapper wrapper = cache.get(accession);
        if (wrapper != null) {
            return (FunctionalInfo) wrapper.get(); // may be null (cached not found)
        }

        UPEntry entry = functionRepo.getEntry(accession);
        FunctionalInfo info = entry != null ? converter.convert(entry) : null;
        cache.put(accession, info);
        LOGGER.info("{} Functional info loaded from {}", accession, info != null ? "database" : "(not found)");
        return info;
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
