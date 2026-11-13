package uk.ac.ebi.protvar.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.time.Duration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Stops download submissions that only produce noise: too many from one client
 * (e.g. a vulnerability scanner sent 359 in 16 min on 2026-11-11) and
 * notification emails to domains that can't receive mail (each one bounces
 * back to us). Both fail open: Redis or DNS trouble never blocks a user.
 */
@Component
@RequiredArgsConstructor
public class DownloadGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadGuard.class);

    public static final String MSG_RATE_LIMITED =
            "Too many download requests. Please try again later.";
    public static final String MSG_BAD_EMAIL =
            "This email address can't receive mail. Please check it, or leave it blank.";

    // RFC 2606 / 6761 reserved names: never deliverable
    private static final List<String> RESERVED_DOMAINS = List.of("example.com", "example.net", "example.org");
    private static final List<String> RESERVED_TLDS = List.of("example", "test", "invalid", "localhost");

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.download.rate-limit.per-hour:100}")
    private int perHour;

    @Value("${cache.version:v1}")
    private String cacheVersion;

    /** True if this client has gone over its hourly download submissions. */
    public boolean rateLimited(HttpServletRequest http) {
        if (perHour <= 0) return false;
        String ip = clientIp(http);
        String key = cacheVersion + ":download:rate:" + ip;
        try {
            Long n = redisTemplate.opsForValue().increment(key);
            if (n != null && n == 1) redisTemplate.expire(key, Duration.ofHours(1));
            if (n != null && n > perHour) {
                if (n == perHour + 1) LOGGER.warn("Download rate limit hit by {}", ip);
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("Rate limit check failed: {}", e.getMessage());
        }
        return false;
    }

    static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }

    /** True if the email's domain is reserved, doesn't exist, or declares it takes no mail. */
    public boolean undeliverable(String email) {
        if (email == null || email.isBlank()) return false;
        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
        return reserved(domain) || rejectsMail(domain);
    }

    static boolean reserved(String domain) {
        String tld = domain.substring(domain.lastIndexOf('.') + 1);
        return RESERVED_TLDS.contains(tld)
                || RESERVED_DOMAINS.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    // NXDOMAIN, or a null MX ("0 .", RFC 7505). No MX at all is fine: mail falls back to the A record.
    static boolean rejectsMail(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attribute mx = ctx.getAttributes(domain, new String[]{"MX"}).get("MX");
                return mx != null && mx.size() == 1 && mx.get(0).toString().trim().endsWith(" .");
            } finally {
                ctx.close();
            }
        } catch (NameNotFoundException e) {
            return true;
        } catch (NamingException e) {
            LOGGER.warn("MX lookup failed for {}: {}", domain, e.getMessage());
            return false;
        }
    }
}
