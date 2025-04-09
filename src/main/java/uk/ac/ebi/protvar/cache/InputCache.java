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
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    private RedisTemplate redisTemplate;

    public String getInput(String id) {
        Object value = redisTemplate.opsForValue().get(CacheKey.input(id));
        return (value != null) ? value.toString() : null;
    }

    public InputBuild getInputBuild(String id) {
        return getFromRedis(CacheKey.inputBuild(id), InputBuild.class);
    }

    public InputSummary getInputSummary(String id) {
        return getFromRedis(CacheKey.inputSummary(id), InputSummary.class);
    }

    public void cacheInputBuild(String id, InputBuild inputBuild) {
        cacheWithExpiry(CacheKey.inputBuild(id), inputBuild);
    }

    public long expires(String id) {
        return redisTemplate.getExpire(CacheKey.input(id));
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
        cacheWithExpiry(CacheKey.input(id), input);

        // Launch async job to generate and cache summary
        executorService.submit(() -> {
            InputSummary inputSummary = InputProcessor.summary(input);
            cacheWithExpiry(CacheKey.inputSummary(id), inputSummary);
        });
    }

    public boolean extend(String id) {
        String key = CacheKey.input(id);
        return extendExpiry(key);
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

    // Redis cache helper functions
    @SuppressWarnings("unchecked")
    private <T> T getFromRedis(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return clazz.isInstance(value) ? (T) value : null;
    }

    private void cacheWithExpiry(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
        redisTemplate.expireAt(key, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
    }

    private boolean extendExpiry(String key) {
        if (redisTemplate.hasKey(key)) {
            redisTemplate.expireAt(key, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
            return true;
        }
        return false;
    }
}