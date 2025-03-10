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

    // Retrieves the latest stats entry for a given release, stats type, and key
    @Query("SELECT s FROM Stats s " +
            "WHERE s.release = :release " +
            "AND s.type = :type " +
            "AND s.key = :key " +
            "ORDER BY s.created DESC")
    Optional<Stats> findLatestStat(@Param("release") String release,
                                   @Param("type") String type,
                                   @Param("key") String key);

    // Retrieves the latest stats entry for each key for a specific release
    @Query("SELECT s FROM Stats s WHERE s.id IN " +
            "(SELECT sub.id FROM Stats sub " +
            "WHERE sub.release = :release " +
            "AND sub.created = (SELECT MAX(s2.created) FROM Stats s2 " +
            "WHERE s2.key = sub.key AND s2.release = :release))")
    List<Stats> findAllLatestStats(@Param("release") String release);
}