package uk.ac.ebi.protvar.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.repo.UniprotAccessionRepo;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * UniProt accession reference set from {@code rel_<rel>_uniprot_accession}: the full known set
 * (canonical + isoforms) plus the canonical subset. Two separate axes:
 *  - {@link #isKnown} — is this a real UniProt accession (existence/validity);
 *  - {@link #isCanonical} / {@link #getCanonicalAccessions} — the SwissProt-curated canonical subset
 *    (used for the unmapped-proteins diff).
 * (Was UniprotEntryCache, which only held the canonical set; isValidEntry there == isCanonical here.)
 * LinkedHashSet preserves the SQL ORDER BY accession order for deterministic endpoint output.
 */
@Repository
public class UniprotAccessionCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniprotAccessionCache.class);

    @Autowired
    private UniprotAccessionRepo uniprotAccessionRepo;

    private final LinkedHashSet<String> known = new LinkedHashSet<>();      // all accessions (canonical + isoforms)
    private final LinkedHashSet<String> canonical = new LinkedHashSet<>();  // is_canonical subset

    @EventListener(classes = ApplicationStartedEvent.class)
    public void loadEntries() {
        LOGGER.info("Loading UniProt accessions into cache");
        uniprotAccessionRepo.findAll().forEach(a -> {
            known.add(a.getAccession());
            if (a.isCanonical())
                canonical.add(a.getAccession());
        });
        LOGGER.info("{} accessions loaded in cache ({} canonical)", known.size(), canonical.size());
    }

    /** Is this a recognised UniProt accession (canonical or isoform) — existence/validity. */
    public boolean isKnown(String accession) {
        return known.contains(accession);
    }

    /** Is this accession the SwissProt-curated canonical representative. */
    public boolean isCanonical(String accession) {
        return canonical.contains(accession);
    }

    public Collection<String> getCanonicalAccessions() {
        return Collections.unmodifiableSet(canonical);
    }

    public Collection<String> getKnownAccessions() {
        return Collections.unmodifiableSet(known);
    }
}
