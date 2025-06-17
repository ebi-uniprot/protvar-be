package uk.ac.ebi.protvar.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.model.CachedUserInputData;

import java.util.List;

@Service
public class UserInputCacheService {
    @Cacheable(value = "userInputs", key = "#inputId")
    //public List<UserInput> getInputs(String inputId) { return null; }
    public List<String> getInputs(String inputId) { return null; }

    @CachePut(value = "userInputs", key = "#inputId")
    //public List<UserInput> cacheInputs(String inputId, List<UserInput> inputs) { return inputs; }
    public List<String> cacheInputs(String inputId, List<String> inputs) { return inputs; }

    // Rename InputBuild to GenomeBuild
    @Cacheable(value = "inputBuilds", key = "#inputId")
    public InputBuild getBuild(String inputId) { return null; }

    @CachePut(value = "inputBuilds", key = "#inputId")
    public InputBuild cacheBuild(String inputId, InputBuild build) { return build; }

    @Cacheable(value = "inputSummaries", key = "#inputId")
    public InputSummary getSummary(String inputId) { return null; }

    @CachePut(value = "inputSummaries", key = "#inputId")
    public InputSummary cacheSummary(String inputId, InputSummary summary) { return summary; }

    public CachedUserInputData getAll(String inputId) {
        return new CachedUserInputData(getInputs(inputId), getBuild(inputId), getSummary(inputId));
    }

    @CacheEvict(value = {"userInputs", "inputBuilds", "inputSummaries"}, key = "#inputId")
    public void clearCache(String inputId) {}

}
