package uk.ac.ebi.protvar.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.repo.UniprotEntryRepo;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

@Repository
public class UniprotEntryCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniprotEntryCache.class);

    @Autowired
    private UniprotEntryRepo uniprotEntryRepo;

    // LinkedHashSet: accessions are unique by definition, so a set models
    // the data correctly and gives O(1) isValidEntry() (called per input
    // variant by Pro2Gen). The "linked" variant preserves the SQL-side
    // ORDER BY accession order so getEntries() iterates in sorted order.
    private LinkedHashSet<String> uniprotEntries = new LinkedHashSet<>();

    /**
     * Load all UniProt accessions for current release. Repo returns them
     * sorted by accession (ORDER BY in SQL), so iteration order is
     * deterministic and aligns with the /mapping/accessions/* endpoints.
     */
    @EventListener(classes = ApplicationStartedEvent.class )
    public void loadEntries() {
        LOGGER.info("Loading UniProt accessions into cache");
        uniprotEntryRepo.findAll().stream()
                .map(entry -> entry.getAccession())
                .forEach(uniprotEntries::add);

        LOGGER.info("{} entries loaded in cache", uniprotEntries.size());
    }

    public boolean isValidEntry(String entry) {
        return uniprotEntries.contains(entry);
    }

    public Collection<String> getEntries() {
        return Collections.unmodifiableSet(uniprotEntries);
    }

}
