package org.zee;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ComscoreNetworkDetectorTest {

    @Test
    void testDetectComscoreFromNonAmpUrl() {
        String url = "https://sb.scorecardresearch.com/b?c1=2&c2=9254297";
        Map<String, String> result = ComscoreNetworkDetector.extractFromUrl(url);

        assertEquals("2", result.get("c1"));
        assertEquals("9254297", result.get("c2"));
    }

    @Test
    void testDetectComscoreFromAmpUrl() {
        String url = "https://sb.scorecardresearch.com/p?c1=2&c2=9254297&c8=title";
        Map<String, String> result = ComscoreNetworkDetector.extractFromUrl(url);

        assertEquals("2", result.get("c1"));
        assertEquals("9254297", result.get("c2"));
    }

    @Test
    void testInvalidUrl() {
        String url = "https://example.com/something?param=val";
        Map<String, String> result = ComscoreNetworkDetector.extractFromUrl(url);

        assertTrue(result.isEmpty());
    }

    @Test
    void testUrlWithMissingC2() {
        String url = "https://sb.scorecardresearch.com/b?c1=2";
        Map<String, String> result = ComscoreNetworkDetector.extractFromUrl(url);

        assertEquals("2", result.get("c1"));
        assertNull(result.get("c2"));
    }

    @Test
    void testUrlWithNoQueryParams() {
        String url = "https://sb.scorecardresearch.com/b";
        Map<String, String> result = ComscoreNetworkDetector.extractFromUrl(url);

        assertTrue(result.isEmpty());
    }
}
