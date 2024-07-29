package uk.ac.ebi.protvar.cache;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static uk.ac.ebi.protvar.config.PagedMapping.INPUT_EXPIRES_AFTER_DAYS;

@AllArgsConstructor
@Repository
public class InputCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputCache.class);

    private RedisTemplate redisTemplate;

    public String cacheFileInput(MultipartFile file) {
        try {
            byte[] b = file.getBytes();
            String id = generateChecksum(b);
            cacheInput(id, new String(b));
            return id;
        } catch (IOException ex) {
            // will default to BAD_REQUEST
            LOGGER.error("Submitted file error", ex);
        }
        return null;
    }

    // TODO: examples shouldn't be cached
    public String cacheTextInput(String text) {
        String id = generateChecksum(text.getBytes());
        cacheInput(id, text);
        return id;
    }

    public void cacheInput(String id, String input) {
        //if (!redisTemplate.hasKey(id)) {
        redisTemplate.opsForValue().set(id, input);
        redisTemplate.expireAt(id, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
        //}
    }

    public boolean extendExpiry(String id) {
        if (redisTemplate.hasKey(id)) {
            redisTemplate.expireAt(id, Instant.now().plus(INPUT_EXPIRES_AFTER_DAYS, ChronoUnit.DAYS));
            return true;
        }
        return false;
    }

    private String generateChecksum(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            String checksum = new BigInteger(1, hash).toString(16);
            return checksum;
        } catch (Exception e) {
            return null;
        }
    }
}