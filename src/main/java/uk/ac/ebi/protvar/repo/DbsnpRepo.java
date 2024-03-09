package uk.ac.ebi.protvar.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.ebi.protvar.model.data.Dbsnp;

import java.util.List;

public interface DbsnpRepo extends CrudRepository<Dbsnp, String> {

    List<Dbsnp> findAllById(Iterable<String> ids);

}
