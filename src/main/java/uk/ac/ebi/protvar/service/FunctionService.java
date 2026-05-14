package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.converter.FunctionalInfoConverter;
import uk.ac.ebi.protvar.model.response.FunctionalInfo;
import uk.ac.ebi.protvar.repo.FunctionRepo;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;

/**
 * Cache evolved from Map<String, DataServiceProtein> cache = new ConcurrentHashMap<>()
 * to Guava cache (offered automatic eviction when cache reaches specified max)
 * Cache<String, DataServiceProtein> cache = CacheBuilder.newBuilder().build()
 * then to disk-based MapDB, and finally to Spring Redis cache (through redisTemplate,
 * now cacheManager and Cacheable annotation).
 *
 * The FUN cache that held the whole FunctionalInfo has been dropped — caching now
 * lives one layer down in {@link FunctionRepo#getHeader(String)} (FUN_HEADER per
 * accession) which holds just the protein-level metadata. Features are fetched
 * fresh per call against an indexed function_feature table, so position-specific
 * calls are accurate without filtering a cached payload in memory.
 */
@Service
@RequiredArgsConstructor
public class FunctionService {
    private final FunctionRepo functionRepo;
    private final FunctionalInfoConverter converter;

    public FunctionalInfo get(String accession) {
        if (accession == null || accession.isEmpty()) return null;
        UPEntry header = functionRepo.getHeader(accession);
        if (header == null) return null;
        return converter.convert(header, functionRepo.getFeatures(accession));
    }

    public FunctionalInfo get(String accession, int position) {
        if (accession == null || accession.isEmpty()) return null;
        UPEntry header = functionRepo.getHeader(accession);
        if (header == null) return null;
        FunctionalInfo info = converter.convert(header, functionRepo.getFeatures(accession, position));
        info.setPosition(position);
        return info;
    }
}
