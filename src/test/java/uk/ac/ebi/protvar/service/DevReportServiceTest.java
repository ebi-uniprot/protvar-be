package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DevReportServiceTest {

    @Test
    void lastWeekSumsThePreviousSevenDays() {
        LocalDate monday = LocalDate.parse("2026-11-16");
        Map<String, Long> counters = Map.of(
                "queued:total", 999L,
                "queued:by-day:2026-11-08", 5L,   // 8 days back: out
                "queued:by-day:2026-11-09", 2L,   // 7 days back: in
                "queued:by-day:2026-11-15", 3L,   // yesterday: in
                "queued:by-day:2026-11-16", 7L,   // today: out
                "failed:by-day:2026-11-12", 1L,
                "rejected:by-day:2026-11-12", 0L);
        assertEquals(Map.of("queued", 5L, "failed", 1L), DevReportService.lastWeek(counters, monday));
    }

    @Test
    void noActivityIsEmpty() {
        assertTrue(DevReportService.lastWeek(Map.of("queued:by-day:2026-01-01", 4L), LocalDate.parse("2026-11-16")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void capturesErrorLogLinesOnly() {
        DevReportService svc = new DevReportService(mock(RedisTemplate.class), mock(DownloadStatusService.class));
        ReflectionTestUtils.setField(svc, "cron", "0 0 8 * * MON");
        svc.captureErrorLogs();
        org.slf4j.Logger log = LoggerFactory.getLogger("uk.ac.ebi.protvar.SomeParser");
        log.warn("not counted");
        log.error("bad input {}", "X", new IllegalStateException("boom"));
        Map<String, ?> counts = (Map<String, ?>) ReflectionTestUtils.getField(svc, "errorCounts");
        assertEquals("1", String.valueOf(counts.get("SomeParser")));
        java.util.Deque<String> recent = (java.util.Deque<String>) ReflectionTestUtils.getField(svc, "recentErrors");
        assertTrue(recent.peekFirst().endsWith("SomeParser: bad input X [java.lang.IllegalStateException]"));
    }
}
