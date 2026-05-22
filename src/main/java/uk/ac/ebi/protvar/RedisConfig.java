package uk.ac.ebi.protvar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import uk.ac.ebi.protvar.config.RetentionProperties;

import java.util.Map;

@Configuration
public class RedisConfig {

    @Value(("${spring.data.redis.host}"))
    private String redisHost;
    @Value(("${spring.data.redis.port}"))
    private int redisPort;

    // Versions every cache key namespace. Bump on a deploy whose @Cacheable
    // model classes have changed shape (renamed/removed fields) — old entries
    // sit on the previous namespace and are naturally invisible to the new BE,
    // so no manual Redis flush is needed. Acceptable cold-cache period on the
    // first startup after a bump (~minutes as caches warm back up).
    @Value("${cache.version:v1}")
    private String cacheVersion;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new JedisConnectionFactory(config);
    }

    // Create a serializer with custom ObjectMapper
    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        // Create and configure ObjectMapper with polymorphic typing
        ObjectMapper mapper = new ObjectMapper();
        // java.time.Instant etc. — without this Jackson throws on serialization
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Tolerate unknown JSON fields on read — safety net for cached entries
        // whose model class has since dropped a field (the primary fix is the
        // cache-version key prefix; this stops the residual 500 if a flush is
        // missed). NB: a *renamed* field will silently null on the new shape.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       GenericJackson2JsonRedisSerializer redisJsonSerializer) {
        // Configure RedisTemplate
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(redisJsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(redisJsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * This ensures that Spring's @Cacheable mechanism will use Jackson (JSON) serialization, not the default JDK one.
     * Caches that hold user-submitted data ({@code inputs}, {@code inputBuilds}, {@code inputSummaries})
     * get a finite TTL from {@link RetentionProperties#getSubmissions()} so old submissions auto-expire;
     * everything else inherits the default config (no TTL, bounded by redis maxmemory + LRU at the cluster).
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     GenericJackson2JsonRedisSerializer redisJsonSerializer,
                                     RetentionProperties retention) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // Prefix every key with the cache version so a deploy with a
                // bumped cache.version moves to a fresh namespace (old keys
                // become orphan, no manual flush needed). Default Spring
                // prefix is "{cacheName}::" — we wrap it to "{version}::{cacheName}::".
                .computePrefixWith(name -> cacheVersion + "::" + name + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisJsonSerializer));

        RedisCacheConfiguration submissionConfig = config.entryTtl(retention.getSubmissions());
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                "inputs", submissionConfig,
                "inputBuilds", submissionConfig,
                "inputSummaries", submissionConfig
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(perCache)
                .build();
    }
}
