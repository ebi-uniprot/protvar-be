package uk.ac.ebi.protvar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.data.Stats;
import uk.ac.ebi.protvar.repo.StatsRepository;

import java.util.List;
import java.util.Optional;

@Service
public class StatsService {
    @Autowired
    private StatsRepository statsRepository;

    public Optional<Stats> getLatestStat(String release, String type, String key) {
        return statsRepository.findLatestStat(release, type, key);
    }

    public List<Stats> getAllLatestStats(String release) {
        return statsRepository.findAllLatestStats(release);
    }
}