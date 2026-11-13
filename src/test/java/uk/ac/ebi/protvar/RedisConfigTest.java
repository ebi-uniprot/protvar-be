package uk.ac.ebi.protvar;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import uk.ac.ebi.protvar.cache.RedisKeys;
import uk.ac.ebi.protvar.config.RetentionProperties;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void cacheTtls() {
        RedisCacheManager manager = (RedisCacheManager) new RedisConfig().cacheManager(
                mock(RedisConnectionFactory.class), new GenericJackson2JsonRedisSerializer(), new RetentionProperties());
        manager.afterPropertiesSet(); // builds the caches from their configs
        Map<String, Duration> ttl = Map.of(
                RedisKeys.Cache.INPUTS, Duration.ofDays(90),
                RedisKeys.Cache.UNIPROT_ENTRY_BY_ACCESSION, Duration.ofDays(30),
                RedisKeys.Cache.STRUCTURES_BY_ACCESSION, Duration.ofDays(30));
        ttl.forEach((cache, expected) -> assertEquals(expected,
                manager.getCacheConfigurations().get(cache).getTtlFunction().getTimeToLive(null, null), cache));
    }
}
