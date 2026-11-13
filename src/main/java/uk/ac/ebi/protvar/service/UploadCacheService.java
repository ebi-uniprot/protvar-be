package uk.ac.ebi.protvar.service;

import uk.ac.ebi.protvar.cache.RedisKeys;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.cache.InputBuild;
import uk.ac.ebi.protvar.cache.InputSummary;
import uk.ac.ebi.protvar.model.CachedInputData;

import java.util.List;

/**
 * todo rethink user cache
 * - cache normalised original input lines
 * - or, converted genomic variants from user inputs for e.g.
 *    [ -- idx, raw input, format, derived genomic variants from input
 *      -- note type is known from format
 *       0, raw, VCF , [chr-pos-ref-alt, ...],
 *       1, raw, HGVS, [chr-pos-ref-alt, ...],
 *       ...
 *       ]
 */
@Service
// Lookups are cache-only (body returns null); "unless" stops an unknown id leaving a null entry.
public class UploadCacheService {
    @Cacheable(value = RedisKeys.Cache.INPUTS, key = "#inputId", unless = "#result == null")
    //public List<VariantInput> getInput(String inputId) { return null; }
    public List<String> getInput(String inputId) { return null; }

    @CachePut(value = RedisKeys.Cache.INPUTS, key = "#inputId")
    //public List<VariantInput> cacheInput(String inputId, List<VariantInput> inputs) { return inputs; }
    public List<String> cacheInput(String inputId, List<String> inputs) { return inputs; }

    // Rename InputBuild to GenomeBuild
    @Cacheable(value = RedisKeys.Cache.INPUT_BUILDS, key = "#inputId", unless = "#result == null")
    public InputBuild getBuild(String inputId) { return null; }

    @CachePut(value = RedisKeys.Cache.INPUT_BUILDS, key = "#inputId")
    public InputBuild cacheBuild(String inputId, InputBuild build) { return build; }

    @Cacheable(value = RedisKeys.Cache.INPUT_SUMMARIES, key = "#inputId", unless = "#result == null")
    public InputSummary getSummary(String inputId) { return null; }

    @CachePut(value = RedisKeys.Cache.INPUT_SUMMARIES, key = "#inputId")
    public InputSummary cacheSummary(String inputId, InputSummary summary) { return summary; }

    public CachedInputData getAll(String inputId) {
        return new CachedInputData(getInput(inputId), getBuild(inputId), getSummary(inputId));
    }

    @CacheEvict(value = {RedisKeys.Cache.INPUTS, RedisKeys.Cache.INPUT_BUILDS, RedisKeys.Cache.INPUT_SUMMARIES}, key = "#inputId")
    public void clearCache(String inputId) {}

}
