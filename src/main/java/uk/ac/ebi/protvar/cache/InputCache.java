package uk.ac.ebi.protvar.cache;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.ebi.protvar.input.processor.InputProcessor;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static uk.ac.ebi.protvar.constants.PagedMapping.INPUT_EXPIRES_AFTER_DAYS;

@AllArgsConstructor
@Repository
public class InputCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputCache.class);

    public static final String INPUT_CACHE_PREFIX = "INPUT-";
    public static final String BUILD_CACHE_PREFIX = "BUILD-";
    public static final String SUMMARY_CACHE_PREFIX = "SUMMARY-";

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);


    private RedisTemplate redisTemplate;

    public String keyOf(String id) {
        return INPUT_CACHE_PREFIX + id;
    }

    public String buildKeyOf(String id) {
        return BUILD_CACHE_PREFIX + id;
    }
    public String summaryKeyOf(String id) {
        return SUMMARY_CACHE_PREFIX + id;
    }

    public String getInput(String id) {
        String key = keyOf(id);
        if (redisTemplate.hasKey(key))
            return redisTemplate.opsForValue().get(key).toString();
        return null;
    }

    public InputBuild getInputBuild(String id) {
        String buildKey = buildKeyOf(id);
        if (redisTemplate.hasKey(buildKey))
            return (InputBuild) redisTemplate.opsForValue().get(buildKey);
        return null;
    }

    public InputSummary getInputSummary(String id) {
        String summaryKey = summaryKeyOf(id);
        if (redisTemplate.hasKey(summaryKey))
            return (InputSummary) redisTemplate.opsForValue().get(summaryKey);
        return null;
    }

    public void cacheInputBuild(String id, InputBuild inputBuild) {
        String buildKey = buildKeyOf(id);
        redisTemplate.opsForValue().set(buildKey, inputBuild);
        redisTemplate.expireAt(buildKey, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
    }

    public long expires(String id) {
        return redisTemplate.getExpire(keyOf(id));
    }

    /**
     * Cache the given file input.
     * @param file
     * @return the file checksum.
     */
    public String cache(MultipartFile file) {
        try {
            byte[] b = file.getBytes();
            String id = checksum(b);
            cacheInput(id, new String(b));
            return id;
        } catch (IOException ex) {
            // will default to BAD_REQUEST
            LOGGER.error("Submitted file error", ex);
        }
        return null;
    }

    /**
     * Cache the given text input.
     * TODO: examples shouldn't be cached; single input shouldn't be cached
     * @param text
     * @return the text checksum.
     */
    public String cache(String text) {
        String id = checksum(text);
        cacheInput(id, text);
        return id;
    }

    private void cacheInput(String id, String input) {
        String key = keyOf(id);
        //if (!redisTemplate.hasKey(key)) {
        redisTemplate.opsForValue().set(key, input);
        redisTemplate.expireAt(key, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
        //}

        // Launch job to generate summary
        executorService.submit(() -> {
            InputSummary inputSummary = InputProcessor.summary(input);
            String summaryKey = summaryKeyOf(id);
            redisTemplate.opsForValue().set(summaryKey, inputSummary);
            redisTemplate.expireAt(summaryKey, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
        });
    }

    public boolean extend(String id) {
        String key = keyOf(id);
        if (redisTemplate.hasKey(key)) {
            redisTemplate.expireAt(key, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
            return true;
        }
        return false;
    }

    /**
     * Generate checksum for the given string.
     * @param s
     * @return
     */
    public static String checksum(String s) {
        return checksum(s.getBytes());
    }

    /**
     * Generate checksum for the given data byte array.
     * @param data
     * @return
     */
    public static String checksum(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            String checksum = new BigInteger(1, hash).toString(16);
            return checksum;
        } catch (Exception e) {
            return null;
        }
    }
}