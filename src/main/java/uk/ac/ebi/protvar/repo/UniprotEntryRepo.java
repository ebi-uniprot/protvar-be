package uk.ac.ebi.protvar.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.ebi.protvar.model.data.UniprotEntry;

import java.util.List;

public interface UniprotEntryRepo extends CrudRepository<UniprotEntry, String> {

    List<UniprotEntry> findAll();

}
