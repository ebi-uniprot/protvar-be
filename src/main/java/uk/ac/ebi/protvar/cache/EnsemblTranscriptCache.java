package uk.ac.ebi.protvar.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.EnsemblTranscript;
import uk.ac.ebi.protvar.repo.EnsemblTranscriptRepo;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-(accession × transcript) Ensembl records (ensp / enspv / ensg / is_mane_select) from
 * {@code rel_<rel>_ensembl_transcript}, keyed by {@code accession:enst} (the grain — one ENST can map to
 * several accessions). Supplies ensp + is_mane_select for the Transcript/Isoform responses.
 */
@Repository
public class EnsemblTranscriptCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnsemblTranscriptCache.class);

    @Autowired
    private EnsemblTranscriptRepo ensemblTranscriptRepo;

    private final Map<String, EnsemblTranscript> transcripts = new HashMap<>();   // accession:enst -> EnsemblTranscript

    @EventListener(classes = ApplicationStartedEvent.class)
    public void load() {
        LOGGER.info("Loading Ensembl transcript records into cache");
        ensemblTranscriptRepo.findAll().forEach(t -> transcripts.put(key(t.getAccession(), t.getEnst()), t));
        LOGGER.info("{} ensembl transcripts loaded in cache", transcripts.size());
    }

    public EnsemblTranscript get(String accession, String enst) {
        return transcripts.get(key(accession, enst));
    }

    private static String key(String accession, String enst) {
        return accession + ":" + enst;
    }
}
