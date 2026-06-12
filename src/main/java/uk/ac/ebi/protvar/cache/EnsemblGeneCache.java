package uk.ac.ebi.protvar.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.EnsemblGene;
import uk.ac.ebi.protvar.repo.EnsemblGeneRepo;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-gene Ensembl records (ensgv / chromosome / reverse_strand) from {@code rel_<rel>_ensembl_gene},
 * keyed by ensg. Supplies reverse_strand (codon/alt-allele math) + the Gene response fields the slim
 * mapping no longer carries.
 */
@Repository
public class EnsemblGeneCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnsemblGeneCache.class);

    @Autowired
    private EnsemblGeneRepo ensemblGeneRepo;

    private final Map<String, EnsemblGene> genes = new HashMap<>();   // ensg -> EnsemblGene

    @EventListener(classes = ApplicationStartedEvent.class)
    public void load() {
        LOGGER.info("Loading Ensembl gene records into cache");
        ensemblGeneRepo.findAll().forEach(g -> genes.put(g.getEnsg(), g));
        LOGGER.info("{} ensembl genes loaded in cache", genes.size());
    }

    public EnsemblGene get(String ensg) {
        return genes.get(ensg);
    }
}
