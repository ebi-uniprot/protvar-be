package uk.ac.ebi.protvar.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.response.ReleaseStat;
import uk.ac.ebi.protvar.repo.ReleaseStatsRepo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StatsService {
    @Autowired
    private ReleaseStatsRepo releaseStatsRepo;

    @Autowired
    private ConfigurableEnvironment environment;

    // The release this BE serves: release_stats holds every release, and a BE only shows its own
    @Value("${uniprot.release}")
    private String release;

    // Every table the BE reads (resolved tbl.* properties), for the version-sync check
    private final Set<String> servedTables = new HashSet<>();

    @PostConstruct
    void collectServedTables() {
        environment.getPropertySources().forEach(source -> {
            if (source instanceof EnumerablePropertySource<?> props) {
                for (String name : props.getPropertyNames()) {
                    if (name.startsWith("tbl.")) {
                        String table = environment.getProperty(name);
                        if (table != null) servedTables.add(table);
                    }
                }
            }
        });
    }

    public List<ReleaseStat> getStats() {
        return releaseStatsRepo.getReleaseStats(release).stream()
                .map(s -> withInSync(s, inSync(s)))
                .toList();
    }

    // false when the counted table is not one the BE serves; null when the figure spans several tables
    private Boolean inSync(ReleaseStat s) {
        String table = s.sourceTable();
        if (table == null || !table.matches("\\w+")) return null;
        return servedTables.contains(table);
    }

    private static ReleaseStat withInSync(ReleaseStat s, Boolean inSync) {
        return new ReleaseStat(s.key(), s.label(), s.description(), s.value(), s.source(), s.version(),
                s.sourceTable(), s.recorded(), inSync);
    }
}
