package uk.ac.ebi.protvar.service;

import uk.ac.ebi.protvar.cache.RedisKeys;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.utils.Email;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dev notifications without the flood:
 * - urgent: download failures email the dev address now, at most once per category per hour;
 * - report: every ERROR log line is counted and the counts, latest errors and the week's
 *   download counters are emailed on app.report.cron. No email when there was no activity.
 * The report is off ("-") unless app.report.cron is set (pub and fb, in protvar-deploy).
 */
@Service
@RequiredArgsConstructor
public class DevReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevReportService.class);
    private static final int RECENT_MAX = 50;
    private static final String OFF = "-";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DownloadStatusService downloadStatusService;

    @Value("${cache.version:v1}")
    private String cacheVersion;
    @Value("${app.report.cron:-}")
    private String cron;
    @Value("${app.report.env:}")
    private String env;

    // ERROR log lines buffered in memory, flushed to Redis every minute so logging stays cheap
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Deque<String> recentErrors = new ConcurrentLinkedDeque<>();

    private String key(String k) {
        return RedisKeys.key(cacheVersion, k);
    }

    /** Email the dev address now, unless this category was already emailed in the last hour. */
    public void urgent(String category, DownloadRequest request, List<String> inputs, Throwable t) {
        boolean first = true;
        try {
            first = !Boolean.FALSE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(key(RedisKeys.Report.SENT + category), 1, Duration.ofHours(1)));
        } catch (Exception e) {
            LOGGER.warn("Urgent throttle check failed: {}", e.getMessage());
        }
        if (first) Email.notifyDevErr(request, inputs, t);
        else LOGGER.info("Dev email for '{}' held: already sent this hour", category);
    }

    @PostConstruct
    void captureErrorLogs() {
        if (OFF.equals(cron)) return;
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent e) {
                if (!e.getLevel().isGreaterOrEqual(Level.ERROR)) return;
                String logger = e.getLoggerName().substring(e.getLoggerName().lastIndexOf('.') + 1);
                errorCounts.computeIfAbsent(logger, k -> new AtomicLong()).incrementAndGet();
                String msg = e.getFormattedMessage();
                if (msg != null && msg.length() > 200) msg = msg.substring(0, 200) + "...";
                String ex = e.getThrowableProxy() != null ? " [" + e.getThrowableProxy().getClassName() + "]" : "";
                recentErrors.addFirst(Instant.now().truncatedTo(ChronoUnit.SECONDS) + " " + logger + ": " + msg + ex);
                while (recentErrors.size() > RECENT_MAX) recentErrors.pollLast();
            }
        };
        appender.setContext(ctx);
        appender.setName("dev-report");
        appender.start();
        ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @Scheduled(fixedDelay = 60_000)
    public void flush() {
        if (OFF.equals(cron)) return;
        try {
            errorCounts.forEach((logger, count) -> {
                long n = count.getAndSet(0);
                if (n > 0) redisTemplate.opsForValue().increment(key(RedisKeys.Report.ERRORS + logger), n);
            });
            String line;
            boolean pushed = false;
            while ((line = recentErrors.pollLast()) != null) { // oldest first, so the list ends newest first
                redisTemplate.opsForList().leftPush(key(RedisKeys.Report.RECENT), line);
                pushed = true;
            }
            if (pushed) redisTemplate.opsForList().trim(key(RedisKeys.Report.RECENT), 0, RECENT_MAX - 1);
        } catch (Exception e) {
            LOGGER.warn("Report flush failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${app.report.cron:-}")
    public void sendReport() {
        flush();
        try {
            // one pod sends it
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key(RedisKeys.Report.LOCK), 1, Duration.ofHours(1))))
                return;
            LocalDate today = LocalDate.now();
            Map<String, Long> downloads = lastWeek(downloadStatusService.getCounters(), today);
            Map<String, Long> errors = readCounters(key(RedisKeys.Report.ERRORS));
            List<Object> recent = redisTemplate.opsForList().range(key(RedisKeys.Report.RECENT), 0, -1);
            if (errors.isEmpty() && downloads.isEmpty()) {
                LOGGER.info("No activity since the last report; nothing sent");
                return;
            }
            long errorTotal = errors.values().stream().mapToLong(Long::longValue).sum();
            StringBuilder body = new StringBuilder()
                    .append("Downloads ").append(today.minusDays(7)).append(" to ").append(today.minusDays(1)).append('\n');
            downloads.forEach((name, n) -> body.append("  ").append(name).append(": ").append(n).append('\n'));
            body.append("\nErrors logged since the last report\n");
            errors.forEach((logger, n) -> body.append("  ").append(logger).append(": ").append(n).append('\n'));
            body.append("\nLatest errors (newest first)\n");
            if (recent != null) recent.forEach(r -> body.append("  ").append(r).append('\n'));

            Email.notifyDev(String.format("ProtVar %s weekly report: %d errors", env, errorTotal), body.toString());

            Set<String> keys = new HashSet<>(Optional.ofNullable(redisTemplate.keys(key(RedisKeys.Report.ERRORS) + "*")).orElse(Set.of()));
            keys.add(key(RedisKeys.Report.RECENT));
            redisTemplate.delete(keys);
        } catch (Exception e) {
            LOGGER.warn("Weekly report failed: {}", e.getMessage());
        }
    }

    /** Sums the per-day download counters (name:by-day:yyyy-mm-dd) over the 7 days before today; zero totals left out. */
    static Map<String, Long> lastWeek(Map<String, Long> counters, LocalDate today) {
        Map<String, Long> out = new TreeMap<>();
        counters.forEach((k, n) -> {
            int i = k.indexOf(":by-day:");
            if (i < 0 || n == null || n == 0) return;
            LocalDate day = LocalDate.parse(k.substring(i + 8));
            if (!day.isBefore(today.minusDays(7)) && day.isBefore(today)) out.merge(k.substring(0, i), n, Long::sum);
        });
        return out;
    }

    private Map<String, Long> readCounters(String prefix) {
        Map<String, Long> out = new TreeMap<>();
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys == null) return out;
        for (String k : keys) {
            Object v = redisTemplate.opsForValue().get(k);
            long n = v instanceof Number num ? num.longValue() : v != null ? Long.parseLong(v.toString()) : 0L;
            if (n > 0) out.put(k.substring(prefix.length()), n);
        }
        return out;
    }
}
