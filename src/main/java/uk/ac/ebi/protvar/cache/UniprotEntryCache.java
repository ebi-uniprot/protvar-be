package uk.ac.ebi.protvar.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.repo.UniprotEntryRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class UniprotEntryCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniprotEntryCache.class);

    @Autowired
    private UniprotEntryRepo uniprotEntryRepo;

    private List<String> uniprotEntries = new ArrayList<>();

    /**
     * Load all UniProt accessions for current release
     */
    @EventListener(classes = ApplicationStartedEvent.class )
    public void loadEntries() {
        LOGGER.info("Loading UniProt entries");
        uniprotEntries.addAll(uniprotEntryRepo.findAll().stream().map(entry -> entry.getAccession()).collect(Collectors.toList()));

        LOGGER.info("{} entries loaded in cache", uniprotEntries.size());
    }

    public boolean isValidEntry(String entry) {
        return uniprotEntries.contains(entry);
    }

}
