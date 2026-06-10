package uk.ac.ebi.protvar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;
import uk.ac.ebi.protvar.config.RetentionProperties;

import java.time.Duration;
import java.util.Map;

@Configuration
public class RedisConfig implements CachingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfig.class);

    @Value(("${spring.data.redis.host}"))
    private String redisHost;
    @Value(("${spring.data.redis.port}"))
    private int redisPort;

    // Socket connect/read timeout for the Jedis client. The Jedis default is
    // only 2000ms — too tight for writing a large cached value (e.g. a
    // FUN_HEADER UPEntry) while Redis is under load from parallel download
    // chunks, which surfaced as "Read timed out" mid-download. Raised here and
    // made configurable per environment.
    @Value("${spring.data.redis.timeout:5000}")
    private int redisTimeoutMs;

    // Connection pool sizing. Jedis pools default to maxTotal=8, which the
    // full-download path can exhaust on its own: up to 10 partition tasks run
    // concurrently (DownloadProcessor.dbTaskSemaphore), each hitting the cache,
    // on top of live API traffic. Borrowing then blocks up to max-wait. Sized
    // above that concurrency so cache ops don't queue behind one another.
    @Value("${spring.data.redis.jedis.pool.max-active:32}")
    private int poolMaxActive;
    @Value("${spring.data.redis.jedis.pool.max-idle:16}")
    private int poolMaxIdle;
    @Value("${spring.data.redis.jedis.pool.min-idle:4}")
    private int poolMinIdle;
    @Value("${spring.data.redis.jedis.pool.max-wait:3000}")
    private int poolMaxWaitMs;

    // Versions every cache key namespace. Bump on a deploy whose cached model
    // classes have changed shape (renamed/removed fields) — old entries sit on
    // the previous namespace and are naturally invisible to the new BE, so no
    // manual Redis flush is needed. Acceptable cold-cache period on the first
    // startup after a bump (~minutes as caches warm back up).
    //
    // Coverage: @Cacheable methods get the prefix automatically via
    // computePrefixWith() below; manual writers (e.g. DownloadStatusService)
    // read the same property and prepend it themselves.
    @Value("${cache.version:v1}")
    private String cacheVersion;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(poolMaxActive);
        poolConfig.setMaxIdle(poolMaxIdle);
        poolConfig.setMinIdle(poolMinIdle);
        poolConfig.setMaxWait(Duration.ofMillis(poolMaxWaitMs));

        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
                .connectTimeout(Duration.ofMillis(redisTimeoutMs))
                .readTimeout(Duration.ofMillis(redisTimeoutMs))
                .usePooling().poolConfig(poolConfig)
                .build();

        return new JedisConnectionFactory(config, clientConfig);
    }

    /**
     * Treat Redis failures as cache misses instead of propagating them. The
     * cache is an optimization layered over the DB; a transient Redis blip
     * (e.g. a "Read timed out" on a SET) must not abort the caller. Before
     * this, a cache PUT timeout inside a @Cacheable getHeader() bubbled all the
     * way up and failed an entire multi-chunk full download — even though the
     * underlying DB read had already succeeded.
     *
     * Swallowing a GET error degrades to a cache miss (method re-executes
     * against the DB); a PUT/EVICT error just means the value isn't (un)cached
     * this time. All are logged at WARN so the underlying Redis issue stays
     * visible.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    static class LoggingCacheErrorHandler implements CacheErrorHandler {
        @Override
        public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
            LOGGER.warn("Redis cache GET failed [{}::{}] — treating as miss: {}", cache.getName(), key, ex.getMessage());
        }
        @Override
        public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
            LOGGER.warn("Redis cache PUT failed [{}::{}] — value not cached: {}", cache.getName(), key, ex.getMessage());
        }
        @Override
        public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
            LOGGER.warn("Redis cache EVICT failed [{}::{}]: {}", cache.getName(), key, ex.getMessage());
        }
        @Override
        public void handleCacheClearError(RuntimeException ex, Cache cache) {
            LOGGER.warn("Redis cache CLEAR failed [{}]: {}", cache.getName(), ex.getMessage());
        }
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
