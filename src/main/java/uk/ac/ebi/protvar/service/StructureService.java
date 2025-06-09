package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.response.Structure;
import uk.ac.ebi.protvar.model.response.StructureResidue;
import uk.ac.ebi.protvar.repo.StructureRepo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StructureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructureService.class);
    private final StructureRepo structureRepo;
    private final CacheManager cacheManager;

    /**
     * Preloads the structure cache with structures for the given accessions.
     * Flow in app:
     * 1. Call structureService.preloadStructureCache(accessions) early in the workflow.
     * 2. Later calls to structureRepo.getStr(accession) will hit the cache.
     *
     * @param accessions List of accession strings to preload.
     */
    public void preloadStructureCache(List<String> accessions) {
        if (accessions == null || accessions.isEmpty()) return;

        Cache cache = cacheManager.getCache("STR");
        if (cache == null) return;

        List<String> toFetch = accessions.stream()
                .filter(acc -> cache.get(acc) == null)
                .toList();

        if (!toFetch.isEmpty()) {
            LOGGER.info("Fetching structures for {} accessions", toFetch.size());
            List<Structure> fetched = structureRepo.getStr(toFetch);

            Map<String, List<Structure>> grouped = fetched.stream()
                    .collect(Collectors.groupingBy(Structure::getAccession));

            for (String acc : toFetch) {
                List<Structure> list = grouped.getOrDefault(acc, Collections.emptyList());
                cache.put(acc, list);
            }
        }
    }

    public List<StructureResidue> getStr(String accession, Integer position) {
        return filterByPosition(structureRepo.getStr(accession), position);
    }

    public List<StructureResidue> filterByPosition(List<Structure> structures, int position) {
        return structures.stream()
                .filter(structure -> structure.getObservedRegions().stream()
                        .anyMatch(range -> range.size() == 2 && position >= range.get(0) && position <= range.get(1)))
                .map(structure -> toStructureResidue(structure, position))
                .toList();
    }

    public static StructureResidue toStructureResidue(Structure structure, int position) {
        StructureResidue residue = new StructureResidue();
        residue.setChainId(structure.getChainId());
        residue.setExperimentalMethod(structure.getExperimentalMethod());
        residue.setPdbId(structure.getPdbId());
        residue.setResolution(structure.getResolution());
        int offset = structure.getStart() - structure.getUnpStart();
        residue.setStart(position + offset);
        return residue;
    }

}
