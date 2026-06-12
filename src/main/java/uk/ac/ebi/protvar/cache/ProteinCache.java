package uk.ac.ebi.protvar.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Protein;
import uk.ac.ebi.protvar.repo.ProteinRepo;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-accession protein metadata (name / gene_name / is_canonical / length) from the
 * {@code rel_<rel>_protein} dim table. Holds exactly the MAPPED accessions (canonical + isoforms), so
 * {@link #isMapped} is the return-gate for protein input, and {@link #get} enriches the mapping rows
 * (gene_name / protein_name / is_canonical) the slim mapping table no longer carries.
 */
@Repository
public class ProteinCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProteinCache.class);

    @Autowired
    private ProteinRepo proteinRepo;

    private final Map<String, Protein> proteins = new HashMap<>();          // accession -> Protein
    private final Set<String> canonicalAccessions = new LinkedHashSet<>();  // is_canonical subset (sorted)

    @EventListener(classes = ApplicationStartedEvent.class)
    public void load() {
        LOGGER.info("Loading protein metadata into cache");
        proteinRepo.findAll().forEach(p -> {
            proteins.put(p.getAccession(), p);
            if (p.isCanonical())
                canonicalAccessions.add(p.getAccession());
        });
        LOGGER.info("{} proteins loaded in cache ({} canonical)", proteins.size(), canonicalAccessions.size());
    }

    /** True if we produced a mapping for this accession (canonical OR isoform) — the return-gate. */
    public boolean isMapped(String accession) {
        return proteins.containsKey(accession);
    }

    public Protein get(String accession) {
        return proteins.get(accession);
    }

    public boolean isCanonical(String accession) {
        Protein p = proteins.get(accession);
        return p != null && p.isCanonical();
    }

    /** Mapped canonical accessions (replaces the old DISTINCT-over-mapping getMappedAccessions). */
    public Set<String> getCanonicalAccessions() {
        return Collections.unmodifiableSet(canonicalAccessions);
    }
}
