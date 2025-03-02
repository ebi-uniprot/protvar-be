package uk.ac.ebi.protvar.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Stats;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatsRepository extends JpaRepository<Stats, Long> {

    // Retrieves the latest stats entry for a given import type and keyName
    @Query("SELECT s FROM Stats s " +
            "WHERE s.importType = :importType " +
            "AND s.keyName = :keyName " +
            "ORDER BY s.createdAt DESC")
    Optional<Stats> findLatestStat(@Param("importType") String importType, @Param("keyName") String keyName);

    // Retrieves the latest stats entry for each keyName
    @Query("SELECT s FROM Stats s WHERE s.id IN " +
            "(SELECT sub.id FROM Stats sub " +
            "WHERE sub.createdAt = (SELECT MAX(s2.createdAt) FROM Stats s2 WHERE s2.keyName = sub.keyName))")
    List<Stats> findAllLatestStats();

    // Retrieves all 'core' dataset stats sorted by creation date
    @Query("SELECT s FROM Stats s WHERE s.datasetType = 'core' ORDER BY s.createdAt DESC")
    List<Stats> findCoreStatsByKeyName();
}