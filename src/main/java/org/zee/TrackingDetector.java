package org.zee;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TrackingDetector {
    private static final Logger logger = LoggerFactory.getLogger(TrackingDetector.class);

    private static final String YES = "Yes";
    private static final String NO = "No";

    public static final Map<String, List<String>> TRACKING_KEYWORDS = Map.of(
            "lotame", List.of("lotame.com", "crwdcntrl.net", "lotame"),
            "chartbeat", List.of("chartbeat.com", "chartbeat.net", "chartbeat"),
            "izooto", List.of("izooto.com", "izooto"),
            "vdo_io", List.of("vdo.ai")
    );

    /**
     * Extracts details about specific tracking services (Lotame, Izooto, Chartbeat, VDO.io)
     * by inspecting script tags, amp-analytics tags, and network requests.
     *
     * @param page        The Playwright Page object.
     * @param metaDetails The map to populate with tracking detection results.
     */
    public static void extractTrackingDetails(Page page, Map<String, String> metaDetails) {
        // Initialize all tracking services to "No"
        TRACKING_KEYWORDS.keySet().forEach(key -> metaDetails.put(key + "_present", NO));

        // Use a Set to store all collected data (script src, inline content, analytics types/configs, request URLs)
        Set<String> allCollectedData = new HashSet<>();

        try {
            // Listener for network requests (will capture requests made AFTER this listener is set up)
            page.onRequest(request -> {
                String requestUrl = request.url().toLowerCase();
                allCollectedData.add(requestUrl);
            });

            // 1. Check <script> tags (both 'src' attributes and inline content)
            List<ElementHandle> scriptElements = page.querySelectorAll("script");
            for (ElementHandle script : scriptElements) {
                String src = script.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
                String content = script.textContent();
                if (content != null && !content.isEmpty()) {
                    allCollectedData.add(content.toLowerCase());
                }
            }

            // 2. Check <amp-analytics> tags (for AMP pages)
            List<ElementHandle> ampAnalyticsElements = page.querySelectorAll("amp-analytics");
            for (ElementHandle ampAnalytics : ampAnalyticsElements) {
                String type = ampAnalytics.getAttribute("type");
                if (type != null && !type.isEmpty()) {
                    allCollectedData.add(type.toLowerCase());
                }

                String config = ampAnalytics.getAttribute("config");
                if (config != null && !config.isEmpty()) {
                    try {
                        JsonElement jsonElement = JsonParser.parseString(config);
                        if (jsonElement.isJsonObject()) {
                            extractStringValuesFromJson(jsonElement.getAsJsonObject(), allCollectedData);
                        }
                    } catch (JsonSyntaxException e) {
                        logger.warn("Warning: Invalid JSON in amp-analytics config on {}: {}", page.url(), e.getMessage());
                    }
                }

                String src = ampAnalytics.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            // 3. Check other AMP components that might load trackers (e.g., amp-pixel src, amp-iframe src)
            List<ElementHandle> ampPixelElements = page.querySelectorAll("amp-pixel[src]");
            for (ElementHandle ampPixel : ampPixelElements) {
                String src = ampPixel.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            List<ElementHandle> ampIframeElements = page.querySelectorAll("amp-iframe[src]");
            for (ElementHandle ampIframe : ampIframeElements) {
                String src = ampIframe.getAttribute("src");
                if (src != null && !src.isEmpty()) {
                    allCollectedData.add(src.toLowerCase());
                }
            }

            // Finally, iterate through all collected data and check against keywords
            for (Map.Entry<String, List<String>> entry : TRACKING_KEYWORDS.entrySet()) {
                String serviceKey = entry.getKey();
                List<String> keywords = entry.getValue();

                boolean found = allCollectedData.stream().anyMatch(data ->
                        keywords.stream().anyMatch(data::contains)
                );

                if (found) {
                    metaDetails.put(serviceKey + "_present", YES);
                }
            }

        } catch (Exception e) {
            logger.warn("Error detecting tracking details for {}: {}", page.url(), e.getMessage());
            // Mark all as "Error" if a general exception occurs during detection
            TRACKING_KEYWORDS.keySet().forEach(key -> metaDetails.put(key + "_present", "Error"));
        }
    }

    // Helper to extract all string values from a JsonObject (for amp-analytics config)
    private static void extractStringValuesFromJson(JsonObject jsonObject, Set<String> targetSet) {
        jsonObject.entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                targetSet.add(value.getAsString().toLowerCase());
            } else if (value.isJsonObject()) {
                extractStringValuesFromJson(value.getAsJsonObject(), targetSet);
            } else if (value.isJsonArray()) {
                value.getAsJsonArray().forEach(arrayElement -> {
                    if (arrayElement.isJsonPrimitive() && arrayElement.getAsJsonPrimitive().isString()) {
                        targetSet.add(arrayElement.getAsString().toLowerCase());
                    } else if (arrayElement.isJsonObject()) {
                        extractStringValuesFromJson(arrayElement.getAsJsonObject(), targetSet);
                    }
                });
            }
        });
    }
}
