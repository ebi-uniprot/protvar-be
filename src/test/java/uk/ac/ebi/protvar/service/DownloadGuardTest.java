package uk.ac.ebi.protvar.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DownloadGuardTest {

    @Test
    void reservedDomains() {
        assertTrue(DownloadGuard.reserved("example.com"));
        assertTrue(DownloadGuard.reserved("mail.example.org"));
        assertTrue(DownloadGuard.reserved("foo.test"));
        assertTrue(DownloadGuard.reserved("localhost"));
        assertFalse(DownloadGuard.reserved("ebi.ac.uk"));
        assertFalse(DownloadGuard.reserved("notexample.com"));
    }

    @Test
    void clientIpPrefersFirstForwardedFor() {
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 10.0.0.1");
        assertEquals("1.2.3.4", DownloadGuard.clientIp(http));
        when(http.getHeader("X-Forwarded-For")).thenReturn(null);
        when(http.getRemoteAddr()).thenReturn("5.6.7.8");
        assertEquals("5.6.7.8", DownloadGuard.clientIp(http));
    }
}
